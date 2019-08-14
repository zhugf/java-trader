package trader.service.ta;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import trader.common.beans.BeansContainer;
import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableData;
import trader.common.exchangeable.ExchangeableTradingTimes;
import trader.common.exchangeable.MarketDayUtil;
import trader.common.tick.PriceLevel;
import trader.common.util.DateUtil;
import trader.service.md.MarketData;
import trader.service.ta.bar.BarBuilder;
import trader.service.ta.bar.FutureBarBuilder;
import trader.service.trade.MarketTimeService;

/**
 * 单个品种的KBar和Listeners
 */
public class TAEntry implements TAItem {
    private final static Logger logger = LoggerFactory.getLogger(TAEntry.class);

    private static class LeveledBarBuilderInfo{
        PriceLevel level;
        BarBuilder barBuilder;
        List<TAListener> listeners = new ArrayList<>();
    }

    private BeansContainer beansContainer;
    private Exchangeable instrument;
    private List<LeveledBarBuilderInfo> levelBuilders = new ArrayList<>();

    public TAEntry(BeansContainer beansContainer, Exchangeable e) {
        this.beansContainer = beansContainer;
        this.instrument = e;
    }

    @Override
    public Exchangeable getInstrument() {
        return instrument;
    }

    @Override
    public LeveledTimeSeries getSeries(PriceLevel level) {
        for(int i=0;i<levelBuilders.size();i++) {
            LeveledBarBuilderInfo barBuilderInfo = levelBuilders.get(i);
            if ( barBuilderInfo.level.equals(level)) {
                return barBuilderInfo.barBuilder.getTimeSeries(level);
            }
        }
        return null;
    }

    public void registerListener(List<PriceLevel> levels, TAListener listener)
    {
        for(PriceLevel level:levels) {
            LeveledBarBuilderInfo builderInfo = null;
            for(int i=0;i<this.levelBuilders.size();i++) {
                if ( level.equals(levelBuilders.get(i).level)) {
                    builderInfo = levelBuilders.get(i);
                    break;
                }
            }
            //需要创建新的LevelBuilderInfo
            if ( builderInfo==null ) {
                builderInfo = new LeveledBarBuilderInfo();
                builderInfo.level = level;
                builderInfo.barBuilder = createBarBuilder(level);
                levelBuilders.add(builderInfo);
            }
            if ( !builderInfo.listeners.contains(listener)) {
                builderInfo.listeners.add(listener);
            }
        }
    }

    /**
     * 加载历史数据. 目前只加载昨天的数据.
     * TODO 加载最近指定KBar数量的数据
     */
    private FutureBarBuilder loadHistoryData(BeansContainer beansContainer, MarketTimeService timeService, ExchangeableData data, PriceLevel level) throws IOException
    {
        TimeSeriesLoader seriesLoader = new TimeSeriesLoader(beansContainer, data).setExchangeable(instrument);
        ExchangeableTradingTimes tradingTimes = instrument.exchange().getTradingTimes(instrument, timeService.getTradingDay());
        if ( tradingTimes==null ) {
            return null;
        }
        seriesLoader
            .setEndTradingDay(tradingTimes.getTradingDay())
            .setStartTradingDay(MarketDayUtil.prevMarketDay(instrument.exchange(), tradingTimes.getTradingDay()))
            .setEndTime(timeService.getMarketTime());

        FutureBarBuilder levelBarBuilder = new FutureBarBuilder(tradingTimes, level);
        levelBarBuilder.loadHistoryData(seriesLoader);
        return levelBarBuilder;
    }

    private BarBuilder createBarBuilder(PriceLevel level) {
        MarketTimeService timeService = beansContainer.getBean(MarketTimeService.class);
        ExchangeableTradingTimes tradingTimes = instrument.exchange().getTradingTimes(instrument, timeService.getTradingDay());

        if ( level.name().toLowerCase().startsWith("min") || level.name().toLowerCase().startsWith("vol")) {
            return new FutureBarBuilder(tradingTimes, level);
        }
        return null;
    }

    /**
     * 根据TICK数据更新KBar
     */
    public void onMarketData(MarketData tick) {
        for(int i=0;i<levelBuilders.size();i++) {
            LeveledBarBuilderInfo barBuilderInfo = levelBuilders.get(i);
            if ( barBuilderInfo.barBuilder.update(tick)) {
                LeveledTimeSeries series = barBuilderInfo.barBuilder.getTimeSeries(barBuilderInfo.level);
                for(TAListener listener:barBuilderInfo.listeners) {
                    try{
                        listener.onNewBar(instrument, series);
                    }catch(Throwable t) {
                        LocalDate tradingDay = beansContainer.getBean(MarketTimeService.class).getTradingDay();
                        logger.error(instrument+" "+DateUtil.date2str(tradingDay)+" "+barBuilderInfo.level+" new bar listener failed: "+t.toString(), t);
                    }
                }
            }
        }
    }

}
