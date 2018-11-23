package trader.service.ta;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
        long[] barEpochMillis;
        volatile int barIndex = -1;

        LevelSeriesInfo(PriceLevel level){
            this.level = level;
        }
    }
    private static final PriceLevel[] minuteLevels = getMinuteLevels();

    private Exchangeable exchangeable;
    private LevelSeriesInfo[] levelSeries;

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
                TreeMap<Integer, Long> index2timestamp = new TreeMap<>();
                LocalDateTime currTime = marketInfo.getMarketOpenTime();
                LocalDateTime endTime = marketInfo.getMarketCloseTime();
                while(currTime.isBefore(endTime) || currTime.isEqual(endTime) ) {
                    int barIndex = TimeSeriesLoader.getBarIndex(exchangeable, level, currTime);
                    if ( barIndex>=0 ) {
                        if  (!index2timestamp.containsKey(barIndex)) {
                            index2timestamp.put(barIndex, DateUtil.localdatetime2long(exchangeable.exchange().getZoneId(), currTime));
                        }
                    }
                    currTime = currTime.plusMinutes(1);
                }
                levelSeries.barEpochMillis = new long[index2timestamp.size()];
                for(Integer barIndex:index2timestamp.keySet()) {
                    long timestamp = index2timestamp.get(barIndex);
                    levelSeries.barEpochMillis[barIndex] = timestamp;
                }
            }
        }
    }

    public Exchangeable getExchangeable() {
        return exchangeable;
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
        if ( logger.isInfoEnabled() ) {
            logger.info(exchangeable+" 历史行情, 加载交易日: "+seriesLoader.getLoadedDates());
        }
        return true;
    }

    public synchronized void onMarketData(MarketData tick) {
        for(PriceLevel level:minuteLevels) {
            LevelSeriesInfo levelSeries = this.levelSeries[level.ordinal()];
            int barIndex = getBarIndex(levelSeries, tick);
            if( barIndex<0 ) { //非开市期间数据, 直接忽略
                continue;
            }
            updateLevelSeries(levelSeries, tick, barIndex);
        }
    }

    private int getBarIndex(LevelSeriesInfo levelSeries, MarketData tick) {
        int result = -1;
        long marketBeginMillis = levelSeries.barEpochMillis[0];
        if ( tick.updateTimestamp>=marketBeginMillis ) {
            int currBarIndex = levelSeries.barIndex;
            if ( currBarIndex<0 ) {
                currBarIndex = 0;
            }
            for(int i=currBarIndex;i<levelSeries.barEpochMillis.length;i++) {
                long barBeginMillis = levelSeries.barEpochMillis[i];
                if ( tick.updateTimestamp < barBeginMillis ) {
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

    private void updateLevelSeries(LevelSeriesInfo levelSeries, MarketData tick, int barIndex) {
        PriceLevel level = levelSeries.level;
        TimeSeries series = levelSeries.series;
        if ( levelSeries.barIndex<0 ) { //第一根KBar
            FutureBar bar = FutureBar.create(tick);
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
                FutureBar bar = FutureBar.create(tick);
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
