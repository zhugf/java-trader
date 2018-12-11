package trader.service.ta;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.TimeSeries;

import trader.common.beans.BeansContainer;
import trader.common.beans.Lifecycle;
import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableData;
import trader.common.exchangeable.MarketDayUtil;
import trader.common.exchangeable.TradingMarketInfo;
import trader.common.tick.PriceLevel;
import trader.common.util.DateUtil;
import trader.common.util.TraderHomeUtil;
import trader.service.md.MarketData;
import trader.service.trade.MarketTimeService;

/**
 * 单个品种的KBar信息
 */
public class TAEntry implements Lifecycle {
    private final static Logger logger = LoggerFactory.getLogger(TAEntry.class);

    private static class LevelSeriesInfo{
        PriceLevel level;
        LeveledTimeSeries series;
        /**
         * 预先计算好时间到KBar的位置, 后续会非常快速的定位.
         */
        long[] barBeginMillis;
        /**
         * 预先计算好的KBar结束时间
         */
        long[] barEndMillis;
        int barIndex = -1;
        boolean newBar = false;

        LevelSeriesInfo(PriceLevel level){
            this.level = level;
        }
    }
    private static final PriceLevel[] minuteLevels = getMinuteLevels();

    private Exchangeable exchangeable;
    private LevelSeriesInfo[] levelSeries;
    private List<LocalDate> historicalDates = Collections.emptyList();

    public TAEntry(Exchangeable exchangeable) {
        this.exchangeable = exchangeable;

        levelSeries = new LevelSeriesInfo[PriceLevel.values().length];
    }

    @Override
    public void init(BeansContainer beansContainer) throws Exception
    {
        MarketTimeService mtService = beansContainer.getBean(MarketTimeService.class);
        ExchangeableData data = TraderHomeUtil.getExchangeableData();
        loadHistoryData(mtService, data);
        buildBarTimestampTable(mtService);
    }

    @Override
    public void destroy() {

    }

    /**
     * 构建时间到KBar位置的数组, 要求开市前调用.
     */
    private void buildBarTimestampTable(MarketTimeService mtService) {
        TradingMarketInfo marketInfo = exchangeable.detectTradingMarketInfo(mtService.getMarketTime());
        if ( marketInfo==null ) {
            logger.info(exchangeable+" 不在交易时间段: "+mtService.getMarketTime());
        }else {
            //按分钟计算Timetsamp到KBar位置表, 后续可以直接查表
            for(PriceLevel level:minuteLevels) {
                LevelSeriesInfo levelSeries = this.levelSeries[level.ordinal()];
                TreeMap<Integer, Long> index2BeginTime = new TreeMap<>();
                LocalDateTime currTime = marketInfo.getMarketOpenTime();
                LocalDateTime endTime = marketInfo.getMarketCloseTime();
                while(currTime.isBefore(endTime) || currTime.isEqual(endTime) ) {
                    int barIndex = TimeSeriesLoader.getBarIndex(exchangeable, level, currTime);
                    if ( barIndex>=0 ) {
                        if  (!index2BeginTime.containsKey(barIndex)) {
                            index2BeginTime.put(barIndex, DateUtil.localdatetime2long(exchangeable.exchange().getZoneId(), currTime));
                        }
                    }
                    currTime = currTime.plusMinutes(1);
                }
                levelSeries.barBeginMillis = new long[index2BeginTime.size()];
                levelSeries.barEndMillis = new long[index2BeginTime.size()];
                for(Integer barIndex:index2BeginTime.keySet()) {
                    long timestamp = index2BeginTime.get(barIndex);
                    levelSeries.barBeginMillis[barIndex] = timestamp;
                    levelSeries.barEndMillis[barIndex] = timestamp+60*1000*level.getMinutePeriod();
                }
            }
        }
    }

    public Exchangeable getExchangeable() {
        return exchangeable;
    }

    public List<LocalDate> getHistoricalDates(){
        return historicalDates;
    }

    public TimeSeries getSeries(PriceLevel level) {
        LevelSeriesInfo levelEntry = levelSeries[level.ordinal()];
        if (levelEntry!=null) {
            return levelEntry.series;
        }
        return null;
    }

    /**
     * 加载历史数据. 目前只加载昨天的数据.
     * TODO 加载最近指定KBar数量的数据
     */
    private boolean loadHistoryData(MarketTimeService timeService, ExchangeableData data) throws IOException
    {
        TimeSeriesLoader seriesLoader = new TimeSeriesLoader(data).setExchangeable(exchangeable);
        LocalDate tradingDay = exchangeable.detectTradingDay(timeService.getMarketTime());
        if ( tradingDay==null ) {
            return false;
        }
        seriesLoader
            .setEndTradingDay(tradingDay)
            .setStartTradingDay(MarketDayUtil.prevMarketDay(exchangeable.exchange(), tradingDay))
            .setEndTime(timeService.getMarketTime());

        for(PriceLevel level:minuteLevels) {
            LevelSeriesInfo levelSeries = new LevelSeriesInfo(level);
            this.levelSeries[level.ordinal()] = levelSeries;
            levelSeries.series = seriesLoader.setLevel(level).load();
        }
        historicalDates = seriesLoader.getLoadedDates();
        return true;
    }

    /**
     * 根据TICK数据更新KBar
     */
    public boolean onMarketData(MarketData tick) {
        boolean result = false;
        for(PriceLevel level:minuteLevels) {
            LevelSeriesInfo levelSeries = this.levelSeries[level.ordinal()];
            int barIndex = getBarIndex(levelSeries, tick);
            if( barIndex<0 ) { //非开市期间数据, 直接忽略
                break;
            }
            boolean levelNewBar = updateLevelSeries(levelSeries, tick, barIndex);
            levelSeries.newBar = levelNewBar;
            result |= levelNewBar;
        }
        return result;
    }

    /**
     * 通知KBar有新增
     */
    public void notifyListeners(List<TAListener> listeners) {
        for(int i=0; i<listeners.size(); i++) {
            TAListener listener = listeners.get(i);
            for(PriceLevel level:minuteLevels) {
                LevelSeriesInfo levelSeries = this.levelSeries[level.ordinal()];
                if ( levelSeries.newBar ) {
                    try{
                        listener.onNewBar(exchangeable, levelSeries.series);
                    }catch(Throwable t) {
                        logger.error("KBar notify "+listener+" failed : "+t.toString(), t);
                    }
                }
            }
        }
    }

    private int getBarIndex(LevelSeriesInfo levelSeries, MarketData tick) {
        int result = -1;
        long marketBeginMillis = levelSeries.barBeginMillis[0];
        if ( tick.updateTimestamp>=marketBeginMillis ) {
            int currBarIndex = levelSeries.barIndex;
            if ( currBarIndex<0 ) {
                currBarIndex = 0;
            }
            for(int i=currBarIndex;i<levelSeries.barBeginMillis.length;i++) {
                if ( tick.updateTimestamp < levelSeries.barBeginMillis[i] ) {
                    break;
                }
                result = i;
            }
            //TimeSeriesLoader.getBarIndex(exchangeable, levelSeries.level, tick.updateTime);
        }else {
            if ( logger.isDebugEnabled() ) {
                logger.debug(exchangeable+" 忽略非市场时间数据 "+tick);
            }
        }
        return result;
    }

    /**
     * 更新TimeSeries
     *
     * @return true 如果产生了新的K线
     */
    private boolean updateLevelSeries(LevelSeriesInfo levelSeries, MarketData tick, int barIndex) {
        boolean result = false;
        PriceLevel level = levelSeries.level;
        TimeSeries series = levelSeries.series;
        if ( levelSeries.barIndex<0 ) { //第一根KBar
            FutureBar bar = FutureBar.create(tick, barIndex);
            series.addBar(bar);
            levelSeries.barIndex = barIndex;
            if ( logger.isDebugEnabled() ) {
                logger.debug(exchangeable+" "+level+" 新K线 #"+barIndex+" 原 #-1 : "+bar);
            }
        } else {
            FutureBar lastBar = (FutureBar)series.getLastBar();
            if ( barIndex==levelSeries.barIndex ) {
                lastBar.update(tick, tick.updateTime);
            } else {
                if ( barIndex==levelSeries.barIndex+1 ) { //如果上一根KBar与这一根KBar相邻
                    lastBar.update(tick, DateUtil.round(tick.updateTime));
                }
                finishBar(levelSeries, lastBar, tick.instrumentId);
                result=true;
                FutureBar bar = FutureBar.create(tick, barIndex);
                if ( logger.isDebugEnabled() ) {
                    logger.debug(exchangeable+" "+level+" 新K线 #"+barIndex+" 原 #"+levelSeries.barIndex+" : "+bar);
                }
                try{
                    series.addBar(bar);
                    levelSeries.barIndex = barIndex;
                }catch(Throwable t){
                    logger.error(exchangeable+" "+level+" 新K线失败 #"+barIndex+" 原 #"+levelSeries.barIndex+" : "+bar, t);
                }
            }
        }
        return result;
    }

    /**
     * 结束一个KBar
     */
    private void finishBar(LevelSeriesInfo levelSeries, FutureBar bar, Exchangeable e) {
        int barIndex = bar.getIndex();
        if ( levelSeries.barEndMillis.length>barIndex ) {
            long endMillis = levelSeries.barEndMillis[barIndex];
            bar.updateEndTime(ZonedDateTime.ofInstant(Instant.ofEpochMilli(endMillis), e.exchange().getZoneId()));
        }
    }

    private static PriceLevel[] getMinuteLevels() {
        List<PriceLevel> minuteLevels = new ArrayList<>();
        for(PriceLevel level:PriceLevel.values()) {
            if ( level.getMinutePeriod()>0 ) {
                minuteLevels.add(level);
            }
        }
        return minuteLevels.toArray(new PriceLevel[minuteLevels.size()]);
    }
}
