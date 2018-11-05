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

    private static PriceLevel[] minuteLevels;

    private Exchangeable exchangeable;
    private TimeSeries[] series;

    public TAEntry(Exchangeable exchangeable) {
        this.exchangeable = exchangeable;
        List<PriceLevel> minuteLevels = new ArrayList<>();
        for(PriceLevel level:PriceLevel.values()) {
            if ( level.getMinutePeriod()>0 ) {
                minuteLevels.add(level);
            }
        }
        this.minuteLevels = minuteLevels.toArray(new PriceLevel[minuteLevels.size()]);
        series = new TimeSeries[PriceLevel.values().length];
    }

    public TimeSeries getSeries(PriceLevel level) {
        return series[level.ordinal()];
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
            series[level.ordinal()] = seriesLoader.setLevel(level).load();
        }
    }

    public void onMarketData(MarketData marketData) {
        for(PriceLevel level:minuteLevels) {
            int tickIndex = TimeSeriesLoader.getTickIndex(exchangeable, level, marketData);
            if( tickIndex>=0 ) {
                //series[level.ordinal()].addBar(bar);
            }
        }
    }

}
