package trader.service.ta;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.TimeSeries;

import trader.common.beans.BeansContainer;
import trader.common.beans.Lifecycle;
import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableData;
import trader.common.exchangeable.ExchangeableTradingTimes;
import trader.common.exchangeable.MarketDayUtil;
import trader.common.tick.PriceLevel;
import trader.common.util.DateUtil;
import trader.common.util.TraderHomeUtil;
import trader.service.md.MarketData;
import trader.service.ta.trend.MarketDataWaveBarBuilder;
import trader.service.ta.trend.WaveBar;
import trader.service.ta.trend.WaveBar.WaveType;
import trader.service.trade.MarketTimeService;

/**
 * 单个品种的KBar信息
 */
public class TAEntry implements TAItem, Lifecycle {
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
        LocalDateTime barBeginTimes[];
        LocalDateTime barEndTimes[];
        int barIndex = -1;
        MarketData lastTick;
        boolean newBar = false;
        LevelSeriesInfo(PriceLevel level){
            this.level = level;
        }
    }
    private static final PriceLevel[] minuteLevels = getMinuteLevels();

    private Exchangeable exchangeable;
    private MarketDataWaveBarBuilder waveBarBuilder;
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
        loadHistoryData(beansContainer, mtService, data);
        buildBarTimestampTable(mtService);
        waveBarBuilder = new MarketDataWaveBarBuilder();
        long threshold = exchangeable.getPriceTick()*3;
        waveBarBuilder.setStrokeDirectionThreshold(new LongNum(threshold));
    }

    @Override
    public void destroy() {

    }

    /**
     * 构建时间到KBar位置的数组, 要求开市前调用.
     */
    private void buildBarTimestampTable(MarketTimeService mtService) {
        ExchangeableTradingTimes tradingTimes = exchangeable.exchange().getTradingTimes(exchangeable, mtService.getTradingDay());
        if ( tradingTimes==null ) {
            logger.info(exchangeable+" 不在交易时间段: "+mtService.getMarketTime());
        }else {
            LocalDateTime mkTime = mtService.getMarketTime();
            //按分钟计算Timetsamp到KBar位置表, 后续可以直接查表
            for(PriceLevel level:minuteLevels) {
                LevelSeriesInfo levelSeries = this.levelSeries[level.ordinal()];
                int barCount = tradingTimes.getTotalTradingSeconds()/(60*level.getMinutePeriod());
                levelSeries.barBeginMillis = new long[barCount];
                levelSeries.barEndMillis = new long[barCount];
                levelSeries.barBeginTimes = new LocalDateTime[barCount];
                levelSeries.barEndTimes = new LocalDateTime[barCount];
                for(int i=0;i<barCount;i++) {
                    LocalDateTime[] barTimes = TimeSeriesLoader.getBarTimes(tradingTimes, level, i, mkTime);
                    levelSeries.barBeginTimes[i] = barTimes[0];
                    levelSeries.barBeginMillis[i] = DateUtil.localdatetime2long(exchangeable.exchange().getZoneId(), barTimes[0]);
                    levelSeries.barEndTimes[i] = barTimes[1];
                    levelSeries.barEndMillis[i] = DateUtil.localdatetime2long(exchangeable.exchange().getZoneId(), barTimes[1]);
                }
            }
        }
    }

    @Override
    public Exchangeable getExchangeable() {
        return exchangeable;
    }

    public List<LocalDate> getHistoricalDates(){
        return historicalDates;
    }

    @Override
    public LeveledTimeSeries getSeries(PriceLevel level) {
        LevelSeriesInfo levelEntry = levelSeries[level.ordinal()];
        if (levelEntry!=null) {
            return levelEntry.series;
        }
        return null;
    }

    @Override
    public List<WaveBar> getWaveBars(WaveType waveType) {
        return waveBarBuilder.getBars(waveType);
    }

    @Override
    public WaveBar getLastWaveBar(WaveType waveType) {
        return waveBarBuilder.getLastBar(waveType);
    }

    /**
     * 加载历史数据. 目前只加载昨天的数据.
     * TODO 加载最近指定KBar数量的数据
     */
    private boolean loadHistoryData(BeansContainer beansContainer, MarketTimeService timeService, ExchangeableData data) throws IOException
    {
        TimeSeriesLoader seriesLoader = new TimeSeriesLoader(beansContainer, data).setExchangeable(exchangeable);
        ExchangeableTradingTimes tradingTimes = exchangeable.exchange().getTradingTimes(exchangeable, timeService.getTradingDay());
        if ( tradingTimes==null ) {
            return false;
        }
        seriesLoader
            .setEndTradingDay(tradingTimes.getTradingDay())
            .setStartTradingDay(MarketDayUtil.prevMarketDay(exchangeable.exchange(), tradingTimes.getTradingDay()))
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
        waveBarBuilder.onMarketData(tick);
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
            FutureBar bar = FutureBar.create(barIndex, levelSeries.barBeginTimes[barIndex], tick, tick);
            series.addBar(bar);
            levelSeries.barIndex = barIndex;
            if ( logger.isDebugEnabled() ) {
                logger.debug(exchangeable+" "+level+" NEW Kbar #"+barIndex+" OLD #-1 : "+bar);
            }
            result = true;
        } else {
            FutureBar lastBar = (FutureBar)series.getLastBar();
            if ( barIndex==levelSeries.barIndex ) {
                lastBar.update(tick, tick.updateTime);
            } else {
                MarketData edgeTick = levelSeries.lastTick;
                LocalDateTime barEndTime = levelSeries.barEndTimes[lastBar.getIndex()];
                if ( tick.updateTime.equals(barEndTime)) {
                    lastBar.update(tick, barEndTime);
                    edgeTick = tick;
                }else {
                    lastBar.updateEndTime(barEndTime.atZone(exchangeable.exchange().getZoneId()));
                }
                result=true;
                FutureBar bar = FutureBar.create(barIndex, levelSeries.barBeginTimes[barIndex], edgeTick, tick);
                if ( logger.isDebugEnabled() ) {
                    logger.debug(exchangeable+" "+level+" NEW Kbar #"+barIndex+" old #"+levelSeries.barIndex+" : "+bar);
                }
                try{
                    series.addBar(bar);
                    levelSeries.barIndex = barIndex;
                }catch(Throwable t){
                    logger.error(exchangeable+" "+level+" failed to NEW Kbar #"+barIndex+" old #"+levelSeries.barIndex+" : "+bar, t);
                }
            }
        }
        levelSeries.lastTick = tick;
        return result;
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
