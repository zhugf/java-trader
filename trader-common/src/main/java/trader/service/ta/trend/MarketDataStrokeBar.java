package trader.service.ta.trend;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableTradingTimes;
import trader.common.util.ConversionUtil;
import trader.common.util.DateUtil;
import trader.common.util.JsonUtil;
import trader.service.md.MarketData;
import trader.service.ta.LongNum;
import trader.service.trade.TradeConstants.PosDirection;

/**
 * 缠轮笔划, 从细微的价格波动中找到的最基本的走势
 */
public class MarketDataStrokeBar extends WaveBar<MarketData> {

    private static final long serialVersionUID = -2463984410565197764L;

    protected WaveBarOption option;
    private MarketData mdOpen;
    private MarketData mdMax;
    private MarketData mdMin;
    private MarketData mdClose;
    private MarketData mdSplit;
    private Duration duration;

    /**
     * 从单个行情切片创建笔划, 方向为Net 未知
     */
    public MarketDataStrokeBar(int index, ExchangeableTradingTimes tradingTimes, WaveBarOption option, MarketData md) {
        super(index, tradingTimes);
        this.option = option;
        mdOpen = mdMax = mdMin = mdClose = md;
        begin = ZonedDateTime.of(md.updateTime, md.instrument.exchange().getZoneId());
        end = begin;
        open = max = min = close = LongNum.fromRawValue(md.lastPrice);
        volume = LongNum.ZERO;
        amount = LongNum.ZERO;
        openInterest = (md.openInterest);
        mktAvgPrice = LongNum.fromRawValue(md.averagePrice);
        avgPrice = close;
        direction = PosDirection.Net;
    }

    /**
     * 从开始结束两个行情切片创建笔划, 方向为Long/Short
     * @param threshold
     * @param md
     * @param md2
     */
    public MarketDataStrokeBar(int index, ExchangeableTradingTimes tradingTimes, WaveBarOption option, MarketData md, MarketData md2) {
        super(index, tradingTimes);
        this.option = option;
        mdOpen = md;
        mdClose = md2;
        begin = ZonedDateTime.of(md.updateTime, md.instrument.exchange().getZoneId());
        end = ZonedDateTime.of(md2.updateTime, md2.instrument.exchange().getZoneId());
        open = LongNum.fromRawValue(md.lastPrice);
        close = LongNum.fromRawValue(md2.lastPrice);
        if ( md.lastPrice<md2.lastPrice ) {
            direction = PosDirection.Long;
            mdMax = md2;
            mdMin = md;
            max = close;
            min = open;
        }else {
            direction = PosDirection.Short;
            mdMax = md;
            mdMin = md2;
            max = open;
            min = close;
        }
        updateVol();
    }

    private MarketDataStrokeBar(ExchangeableTradingTimes tradingTimes, WaveBarOption option, JsonObject json) {
        super(json.get("index").getAsInt(), tradingTimes);
        this.option = option;
        ZoneId zoneId= tradingTimes.getInstrument().exchange().getZoneId();
        direction = ConversionUtil.toEnum(PosDirection.class, json.get("direction").getAsString());
        open = JsonUtil.getPropertyAsNum(json, "open");
        close = JsonUtil.getPropertyAsNum(json, "close");
        max = JsonUtil.getPropertyAsNum(json, "max");
        min = JsonUtil.getPropertyAsNum(json, "min");
        avgPrice = JsonUtil.getPropertyAsNum(json, "avgPrice");
        mktAvgPrice = JsonUtil.getPropertyAsNum(json, "mktAvgPrice");
        openInterest = json.get("openInt").getAsLong();
        volume = JsonUtil.getPropertyAsNum(json, "volume");
        amount = JsonUtil.getPropertyAsNum(json, "turnover");
        begin = JsonUtil.getPropertyAsDateTime(json, "beginTime").atZone(zoneId);
        end = JsonUtil.getPropertyAsDateTime(json, "endTime").atZone(zoneId);
        duration = Duration.ofSeconds( json.get("duration").getAsInt());

        mdOpen = MarketData.fromJson(json.get("mdOpen"));
        mdClose = MarketData.fromJson(json.get("mdClose"));
        mdMax = MarketData.fromJson(json.get("mdMax"));
        mdMin = MarketData.fromJson(json.get("mdMin"));
        if ( json.has("mdSplit")) {
            mdSplit = MarketData.fromJson(json.get("mdSplit"));
        }
    }

    @Override
    public MarketData getOpenTick() {
        return mdOpen;
    }

    @Override
    public MarketData getCloseTick() {
        return mdClose;
    }

    @Override
    public MarketData getMaxTick() {
        return mdMax;
    }

    @Override
    public MarketData getMinTick() {
        return mdMin;
    }

    /**
     * 更新行情切片
     *
     * @return true 如果需要拆分当前笔划, false 不需要, 当前笔划继续.
     */
    @Override
    public WaveBar<MarketData> update(WaveBar<MarketData> prev, MarketData tick) {
        duration = null;
        MarketData prevClose = this.mdClose;
        mdClose = tick;
        end = ZonedDateTime.of(tick.updateTime, tick.instrument.exchange().getZoneId());
        close = LongNum.fromRawValue(tick.lastPrice);
        if (mdMax.lastPrice < tick.lastPrice) {
            mdMax = tick;
            max = close;
        }
        if ( (mdMin.lastPrice > tick.lastPrice) ) {
            mdMin = tick;
            min = close;
        }
//        if ( getDirection()==PosDirection.Short && prevClose.lowestPrice!=tick.lowestPrice && ((LongNum)min).rawValue()>tick.lowestPrice ) {
//            mdMin=tick;
//            min = LongNum.fromRawValue(tick.lowestPrice);
//        }
//        if ( getDirection()==PosDirection.Long &&prevClose.highestPrice!=tick.highestPrice && ((LongNum)max).rawValue()<tick.highestPrice ) {
//            mdMax = tick;
//            max = LongNum.fromRawValue(tick.highestPrice);
//        }

        updateVol();
        //检测方向
        if (direction == PosDirection.Net) {
            if (open.isLessThan(close.minus(option.strokeThreshold))) {
                direction = PosDirection.Long;
            } else if (open.isGreaterThan(close.plus(option.strokeThreshold))) {
                direction = PosDirection.Short;
            }
        }
        if( needSplit() ) {
            return split();
        }
        return null;
    }

    @Override
    public boolean canMerge() {
        return false;
    }

    @Override
    public Duration getTimePeriod(){
        if ( duration==null ){
            Exchangeable instrument = mdOpen.instrument;
            ExchangeableTradingTimes tradingTimes = instrument.exchange().detectTradingTimes(instrument, begin.toLocalDateTime());
            if ( tradingTimes==null ) {
                return Duration.between(begin.toInstant(), end.toInstant());
            }else {
                int beginMillis = tradingTimes.getTradingTime(begin.toLocalDateTime());
                int endMillis = tradingTimes.getTradingTime(end.toLocalDateTime());
                duration = Duration.of(endMillis-beginMillis, ChronoUnit.MILLIS);
            }
        }
        return duration;
    }

    @Override
    public void merge(WaveBar bar) {
        throw new UnsupportedOperationException("merge operation is not supported");
    }

    /**
     * 判断是否需要拆分出新笔划
     */
    private boolean needSplit() {
        boolean result = false;
        switch(direction) {
        case Long:
            //向上笔划, 最高点向下超出阈值, 需要拆分
            result = max.isGreaterThan(close.plus(option.strokeThreshold));
            break;
        case Short:
            //向下笔划, 最低点向上超出阈值, 需要拆分
            result = min.isLessThan(close.minus(option.strokeThreshold));
            break;
        case Net:
            break;
        }
        return result;
    }

    /**
     * 拆分出一个与当前笔划方向相反的新笔划
     */
    private WaveBar<MarketData> split() {
        MarketDataStrokeBar result = null;

        MarketData md0=null, md1=null;
        switch(direction) {
        case Long:
            //向上笔划, 从最高点拆分, 新笔划向下
            md0=mdMax; md1=mdClose;
            this.mdClose = mdMax;
            this.close = max;
            this.end = ZonedDateTime.of(mdMax.updateTime, mdMax.instrument.exchange().getZoneId());
            if ( mdMin.updateTimestamp>mdClose.updateTimestamp ) {
                mdMin = min(mdOpen, mdClose);
            }
            updateVol();
            break;
        case Short:
            //向下笔划, 从最低的拆分, 新笔划向上
            md0=mdMin; md1=mdClose;
            this.mdClose = mdMin;
            this.close = min;
            this.end = ZonedDateTime.of(mdMin.updateTime, mdMin.instrument.exchange().getZoneId());
            if ( mdMax.updateTimestamp>mdClose.updateTimestamp ) {
                mdMax = max(mdOpen, mdClose);
            }
            updateVol();
            break;
        case Net:
            break;
        }
        if ( md0!=null ) {
            result = new MarketDataStrokeBar(index+1, tradingTimes, option, md0, md1);
            mdSplit = md1;
        }
        return result;
    }

    private void updateVol() {
        long vol = mdClose.volume - mdOpen.volume;
        volume = LongNum.valueOf(vol);
        amount = LongNum.fromRawValue(mdClose.turnover - mdOpen.turnover);
        openInterest = (mdClose.openInterest);
        mktAvgPrice = LongNum.fromRawValue(mdClose.averagePrice);

        if ( vol==0 ) {
            avgPrice = LongNum.fromRawValue(mdClose.averagePrice);
        }else {
            long volMultiplier = mdClose.instrument.getVolumeMutiplier();
            avgPrice = LongNum.fromRawValue( (mdClose.turnover - mdOpen.turnover)/(vol*volMultiplier) );
        }
    }

    private static MarketData max(MarketData md, MarketData md2) {
        MarketData result = null;
        if ( md.lastPrice>md2.lastPrice) {
            result= md;
        }else {
            result= md2;
        }
        return result;
    }

    private static MarketData min(MarketData md, MarketData md2) {
        MarketData result = null;
        if ( md.lastPrice<md2.lastPrice) {
            result= md;
        }else {
            result= md2;
        }
        return result;
    }

    @Override
    public String toString() {
        Duration dur= this.getTimePeriod();
        return "Stroke[ "+direction+", B "+DateUtil.date2str(begin.toLocalDateTime())+", "+dur.getSeconds()+"S, O "+open+" C "+close+" H "+max+" L "+min+" ]";
    }

    @Override
    public JsonElement toJson() {
        JsonElement jsonElem = super.toJson();
        JsonObject json = jsonElem.getAsJsonObject();

        json.add("mdOpen", mdOpen.toJson());
        json.add("mdMax", mdMax.toJson());
        json.add("mdMin", mdMin.toJson());
        json.add("mdClose", mdClose.toJson());
        if ( mdSplit!=null ) {
            json.add("mdSplit", mdSplit.toJson());
        }
        return json;
    }

    public static MarketDataStrokeBar fromJson(ExchangeableTradingTimes tradingTimes, WaveBarOption option, JsonElement jsonElem) {
        MarketDataStrokeBar result = new MarketDataStrokeBar(tradingTimes, option, jsonElem.getAsJsonObject());
        return result;
    }

}
