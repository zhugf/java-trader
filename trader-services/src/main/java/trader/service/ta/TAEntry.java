package trader.service.ta;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import trader.common.beans.BeansContainer;
import trader.common.beans.Lifecycle;
import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableData;
import trader.common.exchangeable.ExchangeableTradingTimes;
import trader.common.exchangeable.MarketDayUtil;
import trader.common.exchangeable.MarketTimeStage;
import trader.common.tick.PriceLevel;
import trader.common.util.TraderHomeUtil;
import trader.service.md.MarketData;
import trader.service.ta.bar.FutureBarBuilder;
import trader.service.ta.trend.MarketDataWaveBarBuilder;
import trader.service.ta.trend.WaveBar;
import trader.service.ta.trend.WaveBar.WaveType;
import trader.service.trade.MarketTimeService;

/**
 * 单个品种的KBar信息
 */
@SuppressWarnings("rawtypes")
public class TAEntry implements TAItem, Lifecycle {
    private final static Logger logger = LoggerFactory.getLogger(TAEntry.class);


    private static final PriceLevel[] minuteLevels = getMinuteLevels();

    private Exchangeable exchangeable;
    private MarketDataWaveBarBuilder waveBarBuilder;
    private FutureBarBuilder[] barBuilders;
    private List<LocalDate> historicalDates = Collections.emptyList();

    private ExchangeableTradingTimes tradingTimes;

    public TAEntry(Exchangeable exchangeable) {
        this.exchangeable = exchangeable;
        barBuilders = new FutureBarBuilder[minuteLevels.length];
    }

    @Override
    public void init(BeansContainer beansContainer) throws Exception
    {
        MarketTimeService mtService = beansContainer.getBean(MarketTimeService.class);
        ExchangeableData data = TraderHomeUtil.getExchangeableData();
        loadHistoryData(beansContainer, mtService, data);
        waveBarBuilder = new MarketDataWaveBarBuilder();
        long threshold = exchangeable.getPriceTick()*3;
        waveBarBuilder.setStrokeDirectionThreshold(new LongNum(threshold));
    }

    @Override
    public void destroy() {

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
        FutureBarBuilder levelBarBuilder = barBuilders[level2index(level)];
        if (levelBarBuilder!=null) {
            return levelBarBuilder.getTimeSeries();
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

        for(int i=0;i<minuteLevels.length;i++) {
            PriceLevel level = minuteLevels[i];
            FutureBarBuilder levelBarBuilder = new FutureBarBuilder(tradingTimes, level);
            levelBarBuilder.loadHistoryData(seriesLoader);
            barBuilders[i] = levelBarBuilder;
        }
        historicalDates = seriesLoader.getLoadedDates();
        return true;
    }

    /**
     * 根据TICK数据更新KBar
     */
    public boolean onMarketData(MarketData tick) {
        boolean result = false;
        if ( tick.mktStage==MarketTimeStage.MarketOpen ) {
            waveBarBuilder.onMarketData(tick);
            for(int i=0;i<minuteLevels.length;i++) {
                FutureBarBuilder levelBarBuilder = barBuilders[i];
                result |= levelBarBuilder.update(tick);
            }
        }
        return result;
    }

    /**
     * 通知KBar有新增
     */
    public void notifyListeners(List<TAListener> listeners) {
        for(TAListener listener:listeners) {
            for(int i=0;i<minuteLevels.length;i++) {
                PriceLevel level = minuteLevels[i];
                FutureBarBuilder levelBarBuilder = barBuilders[level2index(level)];
                if ( levelBarBuilder.isNewBar() ) {
                    try{
                        listener.onNewBar(exchangeable, levelBarBuilder.getTimeSeries() );
                    }catch(Throwable t) {
                        logger.error("KBar notify "+listener+" failed : "+t.toString(), t);
                    }
                }
            }
        }
    }

    private static PriceLevel[] getMinuteLevels() {
        return new PriceLevel[]{PriceLevel.MIN1, PriceLevel.MIN3, PriceLevel.MIN5, PriceLevel.MIN15};
    }

    private static int level2index(PriceLevel level) {
        if ( level== PriceLevel.MIN1) {
            return 0;
        }else if ( level== PriceLevel.MIN3) {
            return 1;
        }else if ( level== PriceLevel.MIN5) {
            return 2;
        }else if ( level== PriceLevel.MIN15) {
            return 3;
        }
        return -1;
    }
}
