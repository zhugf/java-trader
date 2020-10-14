package trader.service.ta;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

import org.ta4j.core.num.Num;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import trader.common.exchangeable.ExchangeableTradingTimes;
import trader.common.util.DateUtil;
import trader.common.util.JsonEnabled;
import trader.service.md.MarketData;

public abstract class AbsFutureBar implements FutureBar, JsonEnabled {

    protected MarketData openTick;
    protected MarketData closeTick;
    protected MarketData maxTick;
    protected MarketData minTick;
    protected int index;
    protected Duration timePeriod;
    /** End time of the bar */
    protected ZonedDateTime endTime;
    /** Begin time of the bar */
    protected ZonedDateTime beginTime;
    /** Open price of the period */
    protected Num openPrice = null;
    /** Close price of the period */
    protected Num closePrice = null;
    /** Max price of the period */
    protected Num highPrice = null;
    /** Min price of the period */
    protected Num lowPrice = null;
    /** Traded amount during the period */
    protected Num amount;
    /** Volume of the period */
    protected Num volume;
    protected Num beginVolume;
    protected Num beginAmount;
    protected long beginOpenInt;
    protected Num endVolume;
    protected Num endAmount;
    /** Trade count */
    protected int trades = 0;
    protected long beginMktTime;
    protected long endMktTime;
    protected Num avgPrice;
    protected Num mktAvgPrice;
    protected long openInt;
    protected long endOpenInt;
    protected ExchangeableTradingTimes mktTimes;
    protected Num upperLimit;
    protected Num lowerLimit;


    @Override
    public ExchangeableTradingTimes getTradingTimes() {
        return mktTimes;
    }

    @Override
    public int getIndex() {
        return index;
    }

    @Override
    public Num getOpenPrice() {
        return openPrice;
    }

    @Override
    public Num getLowPrice() {
        return lowPrice;
    }

    @Override
    public Num getHighPrice() {
        return highPrice;
    }

    @Override
    public Num getClosePrice() {
        return closePrice;
    }

    @Override
    public Num getVolume() {
        return volume;
    }

    public Num getBeginVolume() {
        return beginVolume;
    }

    public Num getEndVolume() {
        return endVolume;
    }

    @Override
    public int getTrades() {
        return trades;
    }

    @Override
    public Num getAmount() {
        return amount;
    }

    public Num getBeginAmount() {
        return beginAmount;
    }

    public Num getEndAmount() {
        return endAmount;
    }

    @Override
    public Duration getTimePeriod() {
        return timePeriod;
    }

    @Override
    public ZonedDateTime getBeginTime() {
        return beginTime;
    }

    @Override
    public ZonedDateTime getEndTime() {
        return endTime;
    }

    @Override
    public void addTrade(Num tradeVolume, Num tradePrice) {
        throw new UnsupportedOperationException("addTrade");
    }

    @Override
    public void addPrice(Num price) {
        throw new UnsupportedOperationException("addPrice");
    }

    @Override
    public long getOpenInt() {
        return openInt;
    }

    public long getBeginOpenInt() {
        return beginOpenInt;
    }

    public long getEndOpenInt() {
        return endOpenInt;
    }

    @Override
    public Num getMktAvgPrice() {
        return mktAvgPrice;
    }

    @Override
    public Num getAvgPrice() {
        return avgPrice;
    }

    @Override
    public MarketData getOpenTick() {
        return openTick;
    }

    @Override
    public MarketData getCloseTick() {
        return closeTick;
    }

    @Override
    public MarketData getMaxTick() {
        return maxTick;
    }

    @Override
    public MarketData getMinTick() {
        return minTick;
    }

    protected void setBeginTime(ZonedDateTime beginTime) {
        this.beginTime = beginTime;
        this.beginMktTime = mktTimes.getTradingTime(beginTime.toLocalDateTime());
    }

    public void updateEndTime(ZonedDateTime endTime) {
        this.endTime = endTime;
        this.endMktTime = mktTimes.getTradingTime(endTime.toLocalDateTime());
        timePeriod = Duration.of(endMktTime-beginMktTime, ChronoUnit.MILLIS);
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("index", getIndex());
        json.addProperty("tradingDay", DateUtil.date2str(mktTimes.getTradingDay()));
        json.addProperty("open", getOpenPrice().toString());
        json.addProperty("close", getClosePrice().toString());
        json.addProperty("max", getHighPrice().toString());
        json.addProperty("min", getLowPrice().toString());
        json.addProperty("volume", getVolume().toString());
        json.addProperty("turnover", getAmount().toString());
        json.addProperty("avgPrice", getAvgPrice().toString());
        json.addProperty("openInt", getOpenInt() );
        json.addProperty("beginTime", DateUtil.date2str(getBeginTime().toLocalDateTime()));
        json.addProperty("endTime", DateUtil.date2str(getEndTime().toLocalDateTime()));
        json.addProperty("beginAmount", getBeginAmount().toString());
        json.addProperty("beginVolume", getBeginVolume().toString());
        json.addProperty("beginOpenInt", getBeginOpenInt() );
        json.addProperty("endAmount", getEndAmount().toString());
        json.addProperty("endVolume", getEndVolume().toString());
        json.addProperty("duration", getTimePeriod().getSeconds());
        json.addProperty("mktAvgPrice", getMktAvgPrice().toString());
        json.addProperty("endOpenInt", getEndOpenInt() );
        if ( upperLimit!=null ) {
            json.addProperty("upperLimit", upperLimit.toString());
        }
        if ( lowerLimit!=null ) {
            json.addProperty("lowerLimit", lowerLimit.toString());
        }
        return json;
    }

    @Override
    public String toString() {
        return String.format("{END: %1s, O: %3$6.2f, C: %2$6.2f, L: %4$6.2f, H: %5$6.2f, V: %6$d, OI: %7$d}",
                DateUtil.date2str(getEndTime().toLocalDateTime()), getOpenPrice().doubleValue(), getClosePrice().doubleValue(), getLowPrice().doubleValue(), getHighPrice().doubleValue(), getVolume().longValue(), getOpenInt());
    }

}
