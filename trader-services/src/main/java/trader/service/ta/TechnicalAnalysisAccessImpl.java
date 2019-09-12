package trader.service.ta;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import trader.common.beans.BeansContainer;
import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableData;
import trader.common.exchangeable.ExchangeableTradingTimes;
import trader.common.exchangeable.MarketDayUtil;
import trader.common.tick.PriceLevel;
import trader.common.util.ConversionUtil;
import trader.common.util.DateUtil;
import trader.common.util.JsonEnabled;
import trader.common.util.PriceUtil;
import trader.common.util.StringUtil;
import trader.service.md.MarketData;
import trader.service.ta.bar.BarBuilder;
import trader.service.ta.bar.FutureBarBuilder;
import trader.service.ta.trend.WaveBar;
import trader.service.ta.trend.WaveBar.WaveType;
import trader.service.ta.trend.WaveBarBuilder;
import trader.service.trade.MarketTimeService;

/**
 * 单个品种的KBar和Listeners
 */
public class TechnicalAnalysisAccessImpl implements TechnicalAnalysisAccess, JsonEnabled {
    private final static Logger logger = LoggerFactory.getLogger(TechnicalAnalysisAccessImpl.class);

    private static class LeveledBarBuilderInfo{
        PriceLevel level;
        BarBuilder barBuilder;
    }

    private BeansContainer beansContainer;
    private ExchangeableTradingTimes tradingTimes;
    private Exchangeable instrument;
    private InstrumentDef instrumentDef;
    private List<LeveledBarBuilderInfo> levelBuilders = new ArrayList<>();
    private String cfgVoldailyLevel;
    private PriceLevel voldailyLevel;
    private WaveBarBuilder tickWaveBarBuilder;
    private long[] options = new long[Option.values().length];
    List<TechnicalAnalysisListener> listeners = new ArrayList<>();

    public TechnicalAnalysisAccessImpl(BeansContainer beansContainer, ExchangeableData data, Exchangeable instrument, InstrumentDef instrumentDef) {
        this.beansContainer = beansContainer;
        this.instrument = instrument;
        this.instrumentDef = instrumentDef;
        options[Option.LineWidth.ordinal()] = instrumentDef.lineWidth;
        options[Option.StrokeThreshold.ordinal()] = instrumentDef.strokeThreshold;

        MarketTimeService mtService = beansContainer.getBean(MarketTimeService.class);
        tradingTimes = instrument.exchange().getTradingTimes(instrument, mtService.getTradingDay());

        initBarBuilders(data);
    }

    @Override
    public Exchangeable getInstrument() {
        return instrument;
    }

    public ExchangeableTradingTimes getTradingTimes() {
        return tradingTimes;
    }

    @Override
    public long getOption(Option option) {
        return options[option.ordinal()];
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

    @Override
    public List<PriceLevel> getLevels(){
        List<PriceLevel> result = new ArrayList<>();
        for(LeveledBarBuilderInfo bb:levelBuilders) {
            result.add(bb.level);
        }
        return result;
    }

    @Override
    public PriceLevel getVoldailyLevel() {
        return voldailyLevel;
    }

    @Override
    public List<WaveBar> getWaveBars(PriceLevel level, WaveType waveType) {
        if ( PriceLevel.TICKET.equals(level) ) {
            return tickWaveBarBuilder.getBars(waveType);
        }
        return null;
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("instrument", instrument.uniqueId());

        JsonArray levels = new JsonArray();
        for(LeveledBarBuilderInfo leveledBarBuilder:levelBuilders) {
            JsonObject ljson = new JsonObject();
            ljson.addProperty("level", leveledBarBuilder.level.name());
            ljson.addProperty("barCount", leveledBarBuilder.barBuilder.getTimeSeries(leveledBarBuilder.level).getBarCount());
            levels.add(ljson);
        }
        json.add("levels", levels);
        JsonObject options = new JsonObject();
        for(Option opt:Option.values()) {
            options.addProperty(opt.name(), PriceUtil.long2str(getOption(opt)));
        }
        json.add("options", options);
        if ( voldailyLevel!=null ) {
            json.addProperty("voldailyLevel", voldailyLevel.name());
        }
        return json;
    }

    public void registerListener(TechnicalAnalysisListener listener)
    {
        if ( !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    private void initBarBuilders(ExchangeableData data) {
        TimeSeriesLoader seriesLoader = new TimeSeriesLoader(beansContainer, data).setExchangeable(instrument);
        List<PriceLevel> levels = new ArrayList<>();
        for(String level:instrumentDef.levels) {
            try{
                LeveledBarBuilderInfo leveledBarBuilder = new LeveledBarBuilderInfo();
                if ( level.toLowerCase().startsWith("voldaily")) { //需要动态根据上日的VOLUME决定
                    this.cfgVoldailyLevel = level;
                    continue;
                }else {
                    leveledBarBuilder.level = PriceLevel.valueOf(level);
                }

                if ( leveledBarBuilder.level.prefix().equals(PriceLevel.LEVEL_MIN) || leveledBarBuilder.level.prefix().equals(PriceLevel.LEVEL_DAY) ) {
                    leveledBarBuilder.barBuilder = loadHistoryData(seriesLoader, leveledBarBuilder.level);
                } else {
                    leveledBarBuilder.barBuilder = new FutureBarBuilder(tradingTimes, leveledBarBuilder.level);
                }
                levelBuilders.add(leveledBarBuilder);
                levels.add(leveledBarBuilder.level);
            }catch(Throwable t) {
                logger.error("Load "+instrument+" level "+level+" history data failed", t);
            }
        }
        logger.info("Instrument "+instrument+" bar builders were created for levels: "+levels);
        tickWaveBarBuilder = new WaveBarBuilder();
        tickWaveBarBuilder.getOption().strokeThreshold = LongNum.fromRawValue(instrumentDef.strokeThreshold);
    }

    /**
     * 加载历史数据. 目前只加载昨天的数据.
     * TODO 加载最近指定KBar数量的数据
     */
    private FutureBarBuilder loadHistoryData(TimeSeriesLoader seriesLoader, PriceLevel level) throws IOException
    {
        MarketTimeService mtService = beansContainer.getBean(MarketTimeService.class);
        int dayBefore = 2;
        if ( PriceLevel.DAY.equals(level)) {
            dayBefore = 30;
        } else if ( level.prefix().equals(PriceLevel.LEVEL_MIN)) {
            if ( level.value()>=30 ) {
                dayBefore = 5;
            } else if ( level.value()>=5 ) {
                dayBefore = 2;
            }
        }

        seriesLoader
            .setEndTradingDay(tradingTimes.getTradingDay())
            .setStartTradingDay(MarketDayUtil.nextMarketDays(instrument.exchange(), tradingTimes.getTradingDay(), -1*dayBefore))
            .setEndTime(mtService.getMarketTime());

        FutureBarBuilder levelBarBuilder = new FutureBarBuilder(tradingTimes, level);
        levelBarBuilder.loadHistoryData(seriesLoader);
        return levelBarBuilder;
    }

    /**
     * 根据上日持仓手数, 决定今日的开仓每KBAR的VOLUME数
     */
    private PriceLevel resolveVolDaily(String voldailyLevel, MarketData tick){
        PriceLevel result = null;

        int volMultiplier = 800;
        String levelParts[] = StringUtil.split(voldailyLevel, ":");
        if ( levelParts.length>=2 ) {
            volMultiplier = ConversionUtil.toInt(levelParts[1]);
        }
        if ( volMultiplier<=0 ) {
            volMultiplier = 800;
        }
        long openInt=tick.openInterest;

        if ( openInt>0 ) {
            result = PriceLevel.resolveVolDaily(openInt, volMultiplier);
        }else {
            result = PriceLevel.valueOf("vol1000");
        }
        return result;
    }

    /**
     * 根据TICK数据更新KBar
     */
    public void onMarketData(MarketData tick) {
        //voldailyLevel
        if ( cfgVoldailyLevel!=null ) {
            voldailyLevel = resolveVolDaily(cfgVoldailyLevel, tick);
            if ( voldailyLevel!=null ) {
                LeveledBarBuilderInfo dailyLeveledBarBuilder = new LeveledBarBuilderInfo();
                dailyLeveledBarBuilder.level = voldailyLevel;
                dailyLeveledBarBuilder.barBuilder = new FutureBarBuilder(tradingTimes, dailyLeveledBarBuilder.level);
                levelBuilders.add(dailyLeveledBarBuilder);
                cfgVoldailyLevel = null;
            }
        }

        for(int i=0;i<levelBuilders.size();i++) {
            LeveledBarBuilderInfo leveledBarBuilder = levelBuilders.get(i);
            if ( leveledBarBuilder.barBuilder.update(tick)) {
                LeveledTimeSeries series = leveledBarBuilder.barBuilder.getTimeSeries(leveledBarBuilder.level);
                for(int j=0;j<listeners.size();j++) {
                    TechnicalAnalysisListener listener = listeners.get(j);
                    try{
                        listener.onNewBar(instrument, series);
                    }catch(Throwable t) {
                        LocalDate tradingDay = beansContainer.getBean(MarketTimeService.class).getTradingDay();
                        logger.error(instrument+" "+DateUtil.date2str(tradingDay)+" "+leveledBarBuilder.level+" new bar listener failed: "+t.toString(), t);
                    }
                }
            }
        }
        tickWaveBarBuilder.update(tick);
    }

}
