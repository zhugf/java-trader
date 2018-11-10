package trader.service.ta;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.ta4j.core.TimeSeries;

import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableData;
import trader.common.exchangeable.MarketDayUtil;
import trader.common.tick.PriceLevel;
import trader.service.md.MarketData;

/**
 * 单个品种的KBar信息
 */
public class TAEntry {

    private static class LevelSeries{
        PriceLevel level;
        TimeSeries series;
        int tickIndex = -1;

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
    public void loadHistoryData(ExchangeableData data) throws IOException {
        TimeSeriesLoader seriesLoader = new TimeSeriesLoader(data).setExchangeable(exchangeable);

        seriesLoader
            .setEndDate(LocalDate.now())
            .setBeginDate(MarketDayUtil.prevMarketDay(exchangeable.exchange(), LocalDate.now()));

        for(PriceLevel level:minuteLevels) {
            LevelSeries levelSeries = new LevelSeries(level);
            this.levelSeries[level.ordinal()] = levelSeries;
            levelSeries.series = seriesLoader.setLevel(level).load();
        }
    }

    public void onMarketData(MarketData tick) {
        for(PriceLevel level:minuteLevels) {
            LevelSeries levelSeries = this.levelSeries[level.ordinal()];
            int tickIndex = TimeSeriesLoader.getTickIndex(exchangeable, level, tick);
            if( tickIndex<0 ) { //非开市期间数据, 直接忽略
                continue;
            }
            TimeSeries series = levelSeries.series;
            FutureBar lastBar = (FutureBar)series.getLastBar();
            if ( tickIndex==levelSeries.tickIndex ) {
                lastBar.update(tick);
            } else {
                lastBar.update(tick);
                FutureBar bar = new FutureBar(tick);
                series.addBar(bar);
                levelSeries.tickIndex = tickIndex;
            }
        }
    }

}
