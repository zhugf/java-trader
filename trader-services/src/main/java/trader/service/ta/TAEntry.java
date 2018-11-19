package trader.service.ta;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.TimeSeries;

import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableData;
import trader.common.exchangeable.MarketDayUtil;
import trader.common.tick.PriceLevel;
import trader.common.util.DateUtil;
import trader.service.md.MarketData;
import trader.service.trade.MarketTimeService;

/**
 * 单个品种的KBar信息
 */
public class TAEntry {
    private final static Logger logger = LoggerFactory.getLogger(TAEntry.class);

    private static class LevelSeries{
        PriceLevel level;
        LeveledTimeSeries series;
        volatile int tickIndex = -1;

        LevelSeries(PriceLevel level){
            this.level = level;
        }
    }
    private static PriceLevel[] minuteLevels;

    private Exchangeable exchangeable;
    private LevelSeries[] levelSeries;

    public TAEntry(Exchangeable exchangeable) {
        this.exchangeable = exchangeable;
        List<PriceLevel> minuteLevels = new ArrayList<>();
        for(PriceLevel level:PriceLevel.values()) {
            if ( level.getMinutePeriod()>0 ) {
                minuteLevels.add(level);
            }
        }
        this.minuteLevels = minuteLevels.toArray(new PriceLevel[minuteLevels.size()]);
        levelSeries = new LevelSeries[PriceLevel.values().length];
    }

    public Exchangeable getExchangeable() {
        return exchangeable;
    }

    public TimeSeries getSeries(PriceLevel level) {
        LevelSeries levelEntry = levelSeries[level.ordinal()];
        if (levelEntry!=null) {
            return levelEntry.series;
        }
        return null;
    }

    /**
     * 加载历史数据. 目前只加载昨天的数据.
     * TODO 加载最近指定KBar数量的数据
     */
    public boolean loadHistoryData(MarketTimeService timeService, ExchangeableData data) throws IOException
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
            LevelSeries levelSeries = new LevelSeries(level);
            this.levelSeries[level.ordinal()] = levelSeries;
            levelSeries.series = seriesLoader.setLevel(level).load();
        }
        if ( logger.isInfoEnabled() ) {
            logger.info(exchangeable+" 历史行情, 加载交易日: "+seriesLoader.getLoadedDates());
        }
        return true;
    }

    public void onMarketData(MarketData tick) {
        for(PriceLevel level:minuteLevels) {
            LevelSeries levelSeries = this.levelSeries[level.ordinal()];
            int tickIndex = TimeSeriesLoader.getTickIndex(exchangeable, level, tick);
            if( tickIndex<0 ) { //非开市期间数据, 直接忽略
                continue;
            }
            updateLevelSeries(levelSeries, tick, tickIndex);
        }
    }

    private void updateLevelSeries(LevelSeries levelSeries, MarketData tick, int tickIndex) {
        PriceLevel level = levelSeries.level;
        TimeSeries series = levelSeries.series;
        if ( levelSeries.tickIndex<0 ) { //第一根KBar
            FutureBar bar = FutureBar.create(tick);
            series.addBar(bar);
            levelSeries.tickIndex = tickIndex;
            if ( logger.isDebugEnabled() ) {
                logger.debug(exchangeable+" "+level+" 新K线 #"+tickIndex+" 原 #-1 : "+bar);
            }
        } else {
            FutureBar lastBar = (FutureBar)series.getLastBar();
            if ( tickIndex==levelSeries.tickIndex ) {
                lastBar.update(tick, tick.updateTime);
            } else {
                if ( tickIndex==levelSeries.tickIndex+1 ) { //如果上一根KBar与这一根KBar相邻
                    lastBar.update(tick, DateUtil.round(tick.updateTime));
                }
                FutureBar bar = FutureBar.create(tick);
                if ( logger.isDebugEnabled() ) {
                    logger.debug(exchangeable+" "+level+" 新K线 #"+tickIndex+" 原 #"+levelSeries.tickIndex+" : "+bar);
                }
                try{
                    series.addBar(bar);
                    levelSeries.tickIndex = tickIndex;
                }catch(Throwable t){
                    logger.error(exchangeable+" "+level+" 新K线失败 #"+tickIndex+" 原 #"+levelSeries.tickIndex+" : "+bar, t);
                }
            }
        }
    }

}
