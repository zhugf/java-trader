package trader.service.ta.bar;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableTradingTimes;
import trader.common.exchangeable.MarketTimeStage;
import trader.common.tick.PriceLevel;
import trader.common.util.DateUtil;
import trader.common.util.JsonEnabled;
import trader.common.util.JsonUtil;
import trader.service.md.MarketData;
import trader.service.ta.BaseLeveledTimeSeries;
import trader.service.ta.FutureBar;
import trader.service.ta.LeveledTimeSeries;
import trader.service.ta.LongNum;
import trader.service.ta.TimeSeriesLoader;
/**
 * 实时创建 MIN1-MIN15, VOL1K等等BAR
 */
public class FutureBarBuilder implements BarBuilder, JsonEnabled {
    private final static Logger logger = LoggerFactory.getLogger(FutureBarBuilder.class);

    private ExchangeableTradingTimes tradingTimes;
    private LeveledTimeSeries series;
    private PriceLevel level;

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
    private List<LocalDate> historicalDates = Collections.emptyList();

    public FutureBarBuilder(ExchangeableTradingTimes tradingTimes, PriceLevel level) {
        this.tradingTimes = tradingTimes;
        this.level = level;
        if ( level.name().toLowerCase().startsWith("min")) {
            Exchangeable exchangeable = tradingTimes.getInstrument();
            int barCount = tradingTimes.getTotalTradingMillis()/(1000*60*level.value());
            barBeginMillis = new long[barCount];
            barEndMillis = new long[barCount];
            barBeginTimes = new LocalDateTime[barCount];
            barEndTimes = new LocalDateTime[barCount];
            for(int i=0;i<barCount;i++) {
                LocalDateTime[] barTimes = TimeSeriesLoader.getBarTimes(tradingTimes, level, i, null);
                barBeginTimes[i] = barTimes[0];
                barBeginMillis[i] = DateUtil.localdatetime2long(exchangeable.exchange().getZoneId(), barTimes[0]);
                barEndTimes[i] = barTimes[1];
                barEndMillis[i] = DateUtil.localdatetime2long(exchangeable.exchange().getZoneId(), barTimes[1]);
            }
        }
        series = new BaseLeveledTimeSeries(tradingTimes.getInstrument(), tradingTimes.getInstrument()+"-"+level.toString(), level, LongNum::valueOf);
    }

    public PriceLevel getLevel() {
        return level;
    }

    public List<LocalDate> getHistoricalDates(){
        return historicalDates;
    }

    @Override
    public LeveledTimeSeries getTimeSeries(PriceLevel level) {
        if ( level==this.level ) {
            return series;
        }else {
            return null;
        }
    }

    public FutureBar getLastBar() {
        return (FutureBar)series.getLastBar();
    }

    public boolean hasNewBar() {
        return newBar;
    }

    public void loadHistoryData(TimeSeriesLoader seriesLoader) throws IOException
    {
        this.series = seriesLoader.setLevel(level).load();
        historicalDates = seriesLoader.getLoadedDates();
    }

    @Override
    public boolean update(MarketData tick) {
        boolean result =false;
        if ( tick.mktStage==MarketTimeStage.MarketOpen ) {
            switch(level.prefix()) {
            case PriceLevel.LEVEL_MIN:
                int tickBarIndex = getTimeBarIndex(tick);
                if( tickBarIndex>=0 ) { //非开市期间数据, 直接忽略
                    result = updateTimeBar(tick, tickBarIndex);
                }
                break;
            case PriceLevel.LEVEL_VOL:
                FutureBar lastBar = null;
                if ( series.getBarCount()>0) {
                    lastBar = getLastBar();
                }
                if ( lastBar!=null && lastBar.getVolume().doubleValue()<level.value()) {
                    lastBar.update(tick, tick.updateTime);
                } else {
                    FutureBar bar = FutureBar.fromTicks(++barIndex, tradingTimes, DateUtil.round(tick.updateTime), tick, tick, tick.lastPrice, tick.lastPrice);
                    series.addBar(bar);
                    result = true;
                }
                break;
            case PriceLevel.LEVEL_DAY: //日线不参与当天计算
                break;
            default:
                throw new RuntimeException("Unsupported level: "+level);
            }
            lastTick = tick;
        }
        newBar = result;
        return result;
    }

    private int getTimeBarIndex(MarketData tick) {
        int result = -1;
        long marketBeginMillis = barBeginMillis[0];
        if ( tick.updateTimestamp>=marketBeginMillis ) {
            int currBarIndex = barIndex;
            if ( currBarIndex<0 ) {
                currBarIndex = 0;
            }
            for(int i=currBarIndex;i<barBeginMillis.length;i++) {
                if ( tick.updateTimestamp < barBeginMillis[i] ) {
                    break;
                }
                result = i;
            }
            //TimeSeriesLoader.getBarIndex(exchangeable, levelSeries.level, tick.updateTime);
        }else {
            if ( logger.isDebugEnabled() ) {
                logger.debug(tradingTimes.getInstrument()+" 忽略非市场时间数据 "+tick);
            }
        }
        return result;
    }

    private boolean updateTimeBar(MarketData tick, int tickBarIndex) {
        Exchangeable exchangeable = tradingTimes.getInstrument();
        boolean result = false;
        FutureBar lastBar = null;
        LocalDateTime lastBarEndTime = null;
        if ( series.getBarCount()>0 ) {
            lastBar = (FutureBar)series.getLastBar();
            //需要忽略上一个交易日的Bar
            if ( lastBar.getEndTime().toLocalDateTime().isBefore(tradingTimes.getMarketOpenTime())) {
                lastBar = null;
                lastBarEndTime = null;
            } else {
                lastBarEndTime = barEndTimes[lastBar.getIndex()];
            }
        }
        if ( tickBarIndex==this.barIndex || tick.updateTime.equals(lastBarEndTime) ) {
            lastBar.update(tick, tick.updateTime);
        } else { //创建新的BAR
            MarketData edgeTick = lastTick;
            if ( lastBar!=null ){
                lastBar.updateEndTime(lastBarEndTime.atZone(exchangeable.exchange().getZoneId()));
            }
            result=true;
            FutureBar bar = FutureBar.fromTicks(tickBarIndex, tradingTimes, barBeginTimes[tickBarIndex], edgeTick, tick, tick.lastPrice, tick.lastPrice);
            if ( logger.isDebugEnabled() ) {
                logger.debug(exchangeable+" "+level+" NEW Kbar #"+tickBarIndex+" old #"+this.barIndex+" : "+bar);
            }
            try{
                series.addBar(bar);
                this.barIndex = tickBarIndex;
            }catch(Throwable t){
                logger.error(exchangeable+" "+level+" failed to NEW Kbar #"+tickBarIndex+" old #"+this.barIndex+" : "+bar, t);
            }
        }
        return result;
    }

    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("instrument", tradingTimes.getInstrument().uniqueId());
        json.addProperty("tradingDay", DateUtil.date2str(tradingTimes.getTradingDay()));
        json.addProperty("level", level.toString());
        if ( lastTick!=null ) {
            json.add("lastTick", lastTick.toJson());
        }
        json.addProperty("barIndex", barIndex);
        json.add("historicalDates", JsonUtil.object2json(historicalDates));
        json.add("series", JsonUtil.object2json(series));
        return json;
    }

    public static FutureBarBuilder fromJson(JsonElement jsonElem) {
        JsonObject json = jsonElem.getAsJsonObject();

        PriceLevel level = PriceLevel.valueOf(json.get("level").getAsString());
        Exchangeable instrument = Exchangeable.fromString(json.get("instrument").getAsString());
        LocalDate tradingDay = JsonUtil.getPropertyAsDate(json, "tradingDay");
        ExchangeableTradingTimes tradingTimes = instrument.exchange().getTradingTimes(instrument, tradingDay);
        FutureBarBuilder barBuilder = new FutureBarBuilder(tradingTimes, level);
        barBuilder.barIndex = json.get("barIndex").getAsInt();
        barBuilder.series = BaseLeveledTimeSeries.fromJson(null, json.get("series"));
        if ( json.has("lastTick")) {
            barBuilder.lastTick = MarketData.fromJson(json.get("lastTick"));
        }
        return barBuilder;
    }

}
