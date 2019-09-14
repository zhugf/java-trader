package trader.service.ta;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.ta4j.core.num.Num;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableData;
import trader.common.exchangeable.ExchangeableTradingTimes;
import trader.common.util.CSVDataSet;
import trader.common.util.CSVWriter;
import trader.common.util.ConversionUtil;
import trader.common.util.DateUtil;
import trader.common.util.JsonEnabled;
import trader.common.util.PriceUtil;
import trader.common.util.StringUtil;
import trader.service.md.MarketData;

/**
 * 附带持仓量的KBar
 */
public class FutureBar implements Bar2, JsonEnabled {
    private static final long serialVersionUID = -5989316287411952601L;

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
    protected Num maxPrice = null;
    /** Min price of the period */
    protected Num minPrice = null;
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

    private FutureBar(int index, ExchangeableTradingTimes tradingTimes, LocalDateTime beginTime, MarketData openTick, MarketData closeTick, long high, long low) {
        this(index, tradingTimes);
        this.minPrice = LongNum.fromRawValue(low);
        this.maxPrice = LongNum.fromRawValue(high);
        this.openTick = openTick;
        this.maxTick = openTick;
        this.minTick = openTick;
        this.beginTime = beginTime.atZone(closeTick.instrument.exchange().getZoneId());

        this.beginMktTime = mktTimes.getTradingTime(beginTime);

        if ( index!=0 ) {
            this.openPrice = LongNum.fromRawValue(openTick.lastPrice);
            this.beginAmount = LongNum.fromRawValue(openTick.turnover);
            this.beginVolume = LongNum.valueOf(openTick.volume);
        }else {
            this.openPrice = LongNum.fromRawValue(closeTick.openPrice);
            this.beginAmount = LongNum.ZERO;
            this.beginVolume = LongNum.ZERO;
        }
        this.beginOpenInt = openTick.openInterest;
        update(closeTick, closeTick.updateTime);
    }

    private FutureBar(int index, ExchangeableTradingTimes tradingTimes, List<FutureBar> bars) {
        this(index, tradingTimes);
        FutureBar bar0 = bars.get(0), barn = bars.get(bars.size()-1);
        this.mktTimes = bar0.mktTimes;

        this.beginTime = bar0.getBeginTime();
        this.beginAmount = bar0.getBeginAmount();
        this.openPrice = bar0.getOpenPrice();
        this.openTick = bar0.getOpenTick();
        this.beginVolume = bar0.getBeginVolume();
        this.beginOpenInt = bar0.getBeginOpenInterest();
        this.beginMktTime = bar0.beginMktTime;

        this.endTime = barn.getEndTime();
        this.endAmount = barn.getEndAmount();
        this.closePrice = barn.getClosePrice();
        this.closeTick = barn.getCloseTick();
        this.endVolume = barn.getEndVolume();
        this.openInt = barn.getOpenInterest();
        this.endMktTime = barn.endMktTime;

        this.mktAvgPrice = barn.getMktAvgPrice();
        this.volume = endVolume.minus(beginVolume);
        this.amount = endAmount.minus(beginAmount);
        this.avgPrice = amount.dividedBy( volume.multipliedBy(LongNum.valueOf(tradingTimes.getInstrument().getVolumeMutiplier())) );

        this.maxPrice = bar0.getMaxPrice();
        this.maxTick = bar0.getMaxTick();
        this.minPrice = bar0.getMinPrice();
        this.minTick = bar0.getMaxTick();
        for(int i=1;i<bars.size();i++) {
            FutureBar bar = bars.get(i);
            if ( bar.getMaxPrice().isGreaterThan(this.maxPrice)) {
                this.maxPrice = bar.getMaxPrice();
                this.maxTick = bar.getMaxTick();
            }
            if ( bar.getMinPrice().isLessThan(this.minPrice)) {
                this.minPrice = bar.getMinPrice();
                this.minTick = bar.getMaxTick();
            }
        }
    }

    private FutureBar(int index, ExchangeableTradingTimes tradingTimes) {
        this.index = index;
        this.mktTimes = tradingTimes;
    }

    public int getIndex() {
        return index;
    }

    @Override
    public Num getOpenPrice() {
        return openPrice;
    }

    @Override
    public Num getMinPrice() {
        return minPrice;
    }

    @Override
    public Num getMaxPrice() {
        return maxPrice;
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

    public void update(MarketData tick, LocalDateTime endTime) {
        long priceTick = tick.instrument.getPriceTick(), volMultiplier = tick.instrument.getVolumeMutiplier();
        long newHighestPrice=tick.highestPrice, newLowestPrice=tick.lowestPrice;
        long lastHighestPrice = maxPrice.longValue(), lastLowestPrice= minPrice.longValue();
        this.closeTick = tick;
        this.endTime = endTime.atZone(tick.instrument.exchange().getZoneId());
        long closePrice = tick.lastPrice, maxPrice=0, minPrice=0, barAvgPrice=0;
        maxPrice = ((LongNum)this.maxPrice).rawValue();
        minPrice = ((LongNum)this.minPrice).rawValue();
        this.closePrice = LongNum.fromRawValue(closePrice);
        this.endAmount = LongNum.fromRawValue(tick.turnover);
        this.endVolume = LongNum.valueOf(tick.volume);
        this.openInt = (tick.openInterest);
        this.volume = endVolume.minus(beginVolume);
        this.amount = endAmount.minus(beginAmount);
        int barVol = volume.intValue();
        if ( barVol!=0 ) {
            barAvgPrice = ((LongNum)amount).rawValue()/(barVol*volMultiplier);
        } else {
            barAvgPrice = tick.lastPrice;
        }

        if ( newHighestPrice!=lastHighestPrice && PriceUtil.isValidPrice(newHighestPrice) ) {
            maxPrice = newHighestPrice;
            this.maxTick = tick;
        } else if ( closePrice>maxPrice) {
            maxPrice = closePrice;
            this.maxTick = tick;
        }
        if ( newLowestPrice!=lastLowestPrice && PriceUtil.isValidPrice(newLowestPrice) ) {
            minPrice = newLowestPrice;
            this.minTick = tick;
        } else if ( closePrice<minPrice) {
            minPrice = closePrice;
            this.minTick = tick;
        }

        if ( barAvgPrice> maxPrice) {
            maxPrice = ((barAvgPrice+priceTick/2)/priceTick)*priceTick;
        }
        if ( barAvgPrice<minPrice) {
            minPrice = (barAvgPrice/priceTick)*priceTick;
        }
        this.maxPrice = LongNum.fromRawValue(maxPrice);
        this.minPrice = LongNum.fromRawValue(minPrice);
        this.avgPrice = LongNum.fromRawValue(barAvgPrice);
        mktAvgPrice = LongNum.fromRawValue(tick.averagePrice);
        if ( barAvgPrice>maxPrice || barAvgPrice<minPrice ){
            //System.out.println("avg: "+avgPrice.toString()+", max: "+maxPrice.toString()+", min: "+minPrice.toString());
        }
        this.endMktTime = mktTimes.getTradingTime(tick.updateTime);
        timePeriod = Duration.of(endMktTime-beginMktTime, ChronoUnit.MILLIS);
    }

    public void updateEndTime(ZonedDateTime endTime) {
        this.endTime = endTime;
    }

    @Override
    public String toString() {
        return String.format("{end time: %1s, close price: %2$6.2f, open price: %3$6.2f, min price: %4$6.2f, max price: %5$6.2f, volume: %6$d, openInt: %7$d}",
                DateUtil.date2str(endTime.toLocalDateTime()), closePrice.doubleValue(), openPrice.doubleValue(), minPrice.doubleValue(), maxPrice.doubleValue(), volume.longValue(), openInt);
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("index", index);
        json.addProperty("open", openPrice.toString());
        json.addProperty("close", closePrice.toString());
        json.addProperty("max", maxPrice.toString());
        json.addProperty("min", minPrice.toString());
        json.addProperty("volume", volume.toString());
        json.addProperty("turnover", amount.toString());
        json.addProperty("avgPrice", avgPrice.toString());
        json.addProperty("beginTime", DateUtil.date2str(beginTime.toLocalDateTime()));
        json.addProperty("endTime", DateUtil.date2str(endTime.toLocalDateTime()));
        json.addProperty("duration", getTimePeriod().getSeconds());
        json.addProperty("mktAvgPrice", mktAvgPrice.toString());
        json.addProperty("openInt", openInt);
        return json;
    }

    public static FutureBar fromTicks(int barIndex, ExchangeableTradingTimes tradingTimes, LocalDateTime barBeginTime, MarketData beginTick, MarketData tick, long high, long low) {
        return new FutureBar(barIndex, tradingTimes, barBeginTime, beginTick, tick, high, low);
    }

    public static FutureBar fromBars(int barIndex, ExchangeableTradingTimes tradingTimes, List<FutureBar> bars) {
        return new FutureBar(barIndex, tradingTimes, bars);
    }

    public static FutureBar fromCSV(CSVDataSet csv, Exchangeable instrument, ExchangeableTradingTimes tradingTimes) {
        ZoneId zoneId = instrument.exchange().getZoneId();

        int colIndex = csv.getColumnIndex(ExchangeableData.COLUMN_INDEX);
        int index = csv.getRowIndex();
        if ( colIndex>=0 ) {
            String barIndexStr = csv.get(colIndex);
            if ( !StringUtil.isEmpty(barIndexStr)) {
                index = ConversionUtil.toInt(barIndexStr);
            }
        }
        FutureBar bar = new FutureBar(index, tradingTimes);
        bar.beginTime = csv.getDateTime(ExchangeableData.COLUMN_BEGIN_TIME).atZone(zoneId);
        bar.beginVolume = LongNum.fromRawValue(PriceUtil.price2long(csv.getInt(ExchangeableData.COLUMN_BEGIN_VOLUME)));
        bar.beginAmount = LongNum.fromRawValue(csv.getPrice(ExchangeableData.COLUMN_BEGIN_AMOUNT));
        bar.beginOpenInt = csv.getLong(ExchangeableData.COLUMN_BEGIN_OPENINT);

        bar.endTime = csv.getDateTime(ExchangeableData.COLUMN_END_TIME).atZone(zoneId);
        bar.endVolume = LongNum.fromRawValue(PriceUtil.price2long(csv.getInt(ExchangeableData.COLUMN_BEGIN_VOLUME)));
        bar.endAmount = LongNum.fromRawValue(csv.getPrice(ExchangeableData.COLUMN_END_AMOUNT));
        bar.openInt = csv.getLong(ExchangeableData.COLUMN_BEGIN_OPENINT);

        bar.openPrice = LongNum.fromRawValue(csv.getPrice(ExchangeableData.COLUMN_OPEN));
        bar.closePrice = LongNum.fromRawValue(csv.getPrice(ExchangeableData.COLUMN_CLOSE));
        bar.maxPrice = LongNum.fromRawValue(csv.getPrice(ExchangeableData.COLUMN_HIGH));
        bar.minPrice = LongNum.fromRawValue(csv.getPrice(ExchangeableData.COLUMN_LOW));
        bar.volume = LongNum.fromRawValue(PriceUtil.price2long(csv.getInt(ExchangeableData.COLUMN_VOLUME)));
        bar.amount = LongNum.fromRawValue(csv.getPrice(ExchangeableData.COLUMN_AMOUNT));
        bar.openInt = csv.getLong(ExchangeableData.COLUMN_END_OPENINT);
        bar.mktAvgPrice = LongNum.fromRawValue(csv.getPrice(ExchangeableData.COLUMN_MKTAVG));
        bar.avgPrice = LongNum.fromRawValue(csv.getPrice(ExchangeableData.COLUMN_AVG));

        bar.timePeriod = DateUtil.between(bar.beginTime.toLocalDateTime(), bar.endTime.toLocalDateTime());
        return bar;
    }

    /**
     * 从DAY转换过来
     */
    public static FutureBar fromDayCSV(CSVDataSet csv, Exchangeable instrument) {
        ZoneId zoneId = instrument.exchange().getZoneId();

        int colIndex = csv.getColumnIndex(ExchangeableData.COLUMN_INDEX);
        int index = csv.getRowIndex();
        if ( colIndex>=0 ) {
            String barIndexStr = csv.get(colIndex);
            if ( !StringUtil.isEmpty(barIndexStr)) {
                index = ConversionUtil.toInt(barIndexStr);
            }
        }
        LocalDate date = csv.getDate(ExchangeableData.COLUMN_DATE);
        ExchangeableTradingTimes tradingTimes = instrument.exchange().getTradingTimes(instrument, date);
        FutureBar bar = new FutureBar(index, tradingTimes);
        bar.beginTime = date.atStartOfDay(zoneId);
        bar.beginAmount = LongNum.ZERO;
        bar.beginOpenInt = csv.getLong(ExchangeableData.COLUMN_BEGIN_OPENINT);
        bar.endTime = date.atStartOfDay(zoneId);
        bar.volume = LongNum.fromRawValue(PriceUtil.price2long(csv.getInt(ExchangeableData.COLUMN_VOLUME)));
        bar.endVolume = bar.volume;
        bar.amount = LongNum.fromRawValue(csv.getPrice(ExchangeableData.COLUMN_AMOUNT));
        bar.endAmount = bar.amount;

        bar.openPrice = LongNum.fromRawValue(csv.getPrice(ExchangeableData.COLUMN_OPEN));
        bar.closePrice = LongNum.fromRawValue(csv.getPrice(ExchangeableData.COLUMN_CLOSE));
        bar.maxPrice = LongNum.fromRawValue(csv.getPrice(ExchangeableData.COLUMN_HIGH));
        bar.minPrice = LongNum.fromRawValue(csv.getPrice(ExchangeableData.COLUMN_LOW));
        bar.volume = LongNum.fromRawValue(PriceUtil.price2long(csv.getDouble(ExchangeableData.COLUMN_VOLUME)));
        bar.amount = LongNum.fromRawValue(csv.getPrice(ExchangeableData.COLUMN_AMOUNT));
        bar.openInt = csv.getLong(ExchangeableData.COLUMN_END_OPENINT);
        bar.mktAvgPrice = bar.closePrice;
        bar.avgPrice = bar.closePrice;

        bar.timePeriod = DateUtil.between(bar.beginTime.toLocalDateTime(), bar.endTime.toLocalDateTime());
        return bar;
    }

    public void save(CSVWriter csvWriter) {
        csvWriter.set(ExchangeableData.COLUMN_BEGIN_TIME, DateUtil.date2str(getBeginTime().toLocalDateTime()));
        csvWriter.set(ExchangeableData.COLUMN_END_TIME, DateUtil.date2str(getEndTime().toLocalDateTime()));
        csvWriter.set(ExchangeableData.COLUMN_OPEN, getOpenPrice().toString());
        csvWriter.set(ExchangeableData.COLUMN_HIGH, getMaxPrice().toString());
        csvWriter.set(ExchangeableData.COLUMN_LOW, getMinPrice().toString());
        csvWriter.set(ExchangeableData.COLUMN_CLOSE, getClosePrice().toString());
        csvWriter.set(ExchangeableData.COLUMN_AVG, getAvgPrice().toString());
        csvWriter.set(ExchangeableData.COLUMN_MKTAVG, getMktAvgPrice().toString());
        csvWriter.set(ExchangeableData.COLUMN_VOLUME, ""+getVolume().longValue());
        csvWriter.set(ExchangeableData.COLUMN_AMOUNT, getAmount().toString());

        csvWriter.set(ExchangeableData.COLUMN_BEGIN_VOLUME, ""+getBeginVolume().longValue());
        csvWriter.set(ExchangeableData.COLUMN_BEGIN_AMOUNT, getBeginAmount().toString());
        csvWriter.set(ExchangeableData.COLUMN_BEGIN_OPENINT, ""+getBeginOpenInterest());

        csvWriter.set(ExchangeableData.COLUMN_END_VOLUME, ""+getEndVolume().longValue());
        csvWriter.set(ExchangeableData.COLUMN_END_AMOUNT, getEndAmount().toString());
        csvWriter.set(ExchangeableData.COLUMN_END_OPENINT, ""+getOpenInterest());
        csvWriter.set(ExchangeableData.COLUMN_INDEX, ""+index);
    }

    public void saveDay(CSVWriter csvWriter) {
        csvWriter.set(ExchangeableData.COLUMN_DATE, DateUtil.date2str(mktTimes.getTradingDay()));
        csvWriter.set(ExchangeableData.COLUMN_OPEN, getOpenPrice().toString());
        csvWriter.set(ExchangeableData.COLUMN_HIGH, getMaxPrice().toString());
        csvWriter.set(ExchangeableData.COLUMN_LOW, getMinPrice().toString());
        csvWriter.set(ExchangeableData.COLUMN_CLOSE, getClosePrice().toString());
        csvWriter.set(ExchangeableData.COLUMN_VOLUME, ""+getVolume().longValue());
        csvWriter.set(ExchangeableData.COLUMN_AMOUNT, getAmount().toString());
        csvWriter.set(ExchangeableData.COLUMN_BEGIN_OPENINT, ""+getBeginOpenInterest());
        csvWriter.set(ExchangeableData.COLUMN_END_OPENINT, ""+getOpenInterest());
    }

}
