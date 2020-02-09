package trader.service.ta;

import java.time.Duration;
import java.time.ZonedDateTime;

import org.ta4j.core.num.Num;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import trader.common.exchangeable.ExchangeableTradingTimes;
import trader.common.util.DateUtil;
import trader.common.util.JsonEnabled;
import trader.service.md.MarketData;

public abstract class AbsBar2 implements Bar2, JsonEnabled {

    protected Num avgPrice;
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
    protected Num mktAvgPrice;
    protected long openInt;
    protected ExchangeableTradingTimes mktTimes;


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

    public long getBeginOpenInterest() {
        return beginOpenInt;
    }

    @Override
    public long getOpenInterest() {
        return openInt;
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

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("index", index);
        json.addProperty("tradingDay", DateUtil.date2str(mktTimes.getTradingDay()));
        json.addProperty("open", openPrice.toString());
        json.addProperty("close", closePrice.toString());
        json.addProperty("max", highPrice.toString());
        json.addProperty("min", lowPrice.toString());
        json.addProperty("volume", volume.toString());
        json.addProperty("turnover", amount.toString());
        json.addProperty("avgPrice", avgPrice.toString());
        json.addProperty("beginTime", DateUtil.date2str(beginTime.toLocalDateTime()));
        json.addProperty("endTime", DateUtil.date2str(endTime.toLocalDateTime()));
        json.addProperty("beginAmount", beginAmount.toString());
        json.addProperty("beginVolume", beginVolume.toString());
        json.addProperty("beginOpenInt", beginOpenInt);
        json.addProperty("endAmount", endAmount.toString());
        json.addProperty("endVolume", endVolume.toString());
        json.addProperty("duration", getTimePeriod().getSeconds());
        json.addProperty("mktAvgPrice", mktAvgPrice.toString());
        json.addProperty("openInt", openInt);
        return json;
    }

    @Override
    public String toString() {
        return String.format("{END: %1s, O: %3$6.2f, C: %2$6.2f, L: %4$6.2f, H: %5$6.2f, V: %6$d, OI: %7$d}",
                DateUtil.date2str(getEndTime().toLocalDateTime()), getOpenPrice().doubleValue(), getClosePrice().doubleValue(), getLowPrice().doubleValue(), getHighPrice().doubleValue(), getVolume().longValue(), getOpenInterest());
    }

}
