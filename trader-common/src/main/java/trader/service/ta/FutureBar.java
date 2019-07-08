package trader.service.ta;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

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
    protected LongNum openPrice = null;
    /** Close price of the period */
    protected LongNum closePrice = null;
    /** Max price of the period */
    protected LongNum maxPrice = null;
    /** Min price of the period */
    protected LongNum minPrice = null;
    /** Traded amount during the period */
    protected LongNum amount;
    /** Volume of the period */
    protected Num volume;
    /** Trade count */
    protected int trades = 0;
    protected long beginMktTime;
    protected long endMktTime;
    protected LongNum mktAvgPrice;
    protected long openInterest;
    protected ExchangeableTradingTimes mktTimes;

    private FutureBar(int index, ExchangeableTradingTimes tradingTimes, LocalDateTime beginTime, MarketData openTick, MarketData closeTick, long high, long low) {
        this.index = index;
        this.beginTime = beginTime.atZone(closeTick.instrumentId.exchange().getZoneId());
        this.minPrice = LongNum.fromRawValue(low);
        this.maxPrice = LongNum.fromRawValue(high);
        this.openTick = openTick;
        this.maxTick = openTick;
        this.minTick = openTick;
        mktTimes = tradingTimes;
        this.beginMktTime = mktTimes.getTradingTime(beginTime);
        if ( openTick!=null ) {
            this.openPrice = LongNum.fromRawValue(openTick.lastPrice);
        }else {
            this.openPrice = LongNum.fromRawValue(closeTick.openPrice);
        }
        update(closeTick, closeTick.updateTime);
    }

    private FutureBar() {

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

    @Override
    public int getTrades() {
        return trades;
    }

    @Override
    public Num getAmount() {
        return amount;
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
    public long getOpenInterest() {
        return openInterest;
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
        long priceTick = tick.instrumentId.getPriceTick(), volMultiplier = tick.instrumentId.getVolumeMutiplier();
        MarketData lastTick = this.closeTick;
        this.closeTick = tick;
        if ( lastTick==null ) {
            lastTick = tick;
        }
        long lastHighestPrice = lastTick.highestPrice, lastLowestPrice=lastTick.lowestPrice, newHighestPrice=tick.highestPrice, newLowestPrice=tick.lowestPrice;
        this.endTime = endTime.atZone(tick.instrumentId.exchange().getZoneId());
        long closePrice = tick.lastPrice, maxPrice=0, minPrice=0, barAvgPrice=0;
        maxPrice = this.maxPrice.rawValue();
        minPrice = this.minPrice.rawValue();
        this.closePrice = LongNum.fromRawValue(closePrice);
        long barVol=0,barAmt=0;
        if ( openTick!=null ) {
            barVol = (tick.volume-openTick.volume);
            barAmt = tick.turnover-openTick.turnover;
            if ( barVol>0 ) {
                barAvgPrice = barAmt/(barVol*volMultiplier);
            } else {
                barAvgPrice = closePrice;
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
        }else {
            barVol = tick.volume;
            barAmt= tick.turnover;
            barAvgPrice = (tick.averagePrice);
            if ( !PriceUtil.isValidPrice(barAvgPrice) ) {
                if (barVol!=0 ) {
                    barAvgPrice = barAmt/(barVol*volMultiplier);
                } else {
                    barAvgPrice = tick.lastPrice;
                }
            }
            if ( PriceUtil.isValidPrice(newHighestPrice) ) {
                maxPrice = newHighestPrice;
            }
            if( PriceUtil.isValidPrice(newLowestPrice) ) {
                minPrice = newLowestPrice;
            }
        }
        this.openInterest = (tick.openInterest);
        this.volume = LongNum.valueOf(barVol);
        this.amount = LongNum.fromRawValue(barAmt);
        if ( barAvgPrice+10*priceTick<minPrice) {
            //System.out.println(tick.instrumentId+" barAvgPrice "+barAvgPrice+" minPrice: "+minPrice+" maxPrice "+maxPrice+" mktAvgPrice "+tick.averagePrice);
        }
        if ( barAvgPrice> maxPrice) {
            maxPrice = ((barAvgPrice+priceTick/2)/priceTick)*priceTick;
        }
        if ( barAvgPrice<minPrice) {
            minPrice = (barAvgPrice/priceTick)*priceTick;
        }
        this.maxPrice = LongNum.fromRawValue(maxPrice); this.minPrice = LongNum.fromRawValue(minPrice);
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
                DateUtil.date2str(endTime.toLocalDateTime()), closePrice.doubleValue(), openPrice.doubleValue(), minPrice.doubleValue(), maxPrice.doubleValue(), volume.longValue(), openInterest);
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
        json.addProperty("amount", amount.toString());
        json.addProperty("avgPrice", avgPrice.toString());
        json.addProperty("beginTime", DateUtil.date2str(beginTime.toLocalDateTime()));
        json.addProperty("endTime", DateUtil.date2str(endTime.toLocalDateTime()));
        json.addProperty("duration", getTimePeriod().getSeconds());
        json.addProperty("mktAvgPrice", mktAvgPrice.toString());
        json.addProperty("openInt", openInterest);
        return json;
    }

    public static FutureBar create(int barIndex, ExchangeableTradingTimes tradingTimes, LocalDateTime barBeginTime, MarketData beginTick, MarketData tick, long high, long low) {
        FutureBar bar = null;
        bar = new FutureBar(barIndex, tradingTimes, barBeginTime, beginTick, tick, high, low);
        return bar;
    }

    public static FutureBar fromCSV(CSVDataSet csv, Exchangeable exchangeable) {
        FutureBar bar = new FutureBar();
        ZoneId zoneId = exchangeable.exchange().getZoneId();

        int colIndex = csv.getColumnIndex(ExchangeableData.COLUMN_INDEX);
        bar.index = csv.getRowIndex();
        if ( colIndex>=0 ) {
            String barIndexStr = csv.get(colIndex);
            if ( !StringUtil.isEmpty(barIndexStr)) {
                bar.index = ConversionUtil.toInt(barIndexStr);
            }
        }
        bar.beginTime = csv.getDateTime(ExchangeableData.COLUMN_BEGIN_TIME).atZone(zoneId);
        bar.endTime = csv.getDateTime(ExchangeableData.COLUMN_END_TIME).atZone(zoneId);
        bar.openPrice = LongNum.fromRawValue(csv.getPrice(ExchangeableData.COLUMN_OPEN));
        bar.closePrice = LongNum.fromRawValue(csv.getPrice(ExchangeableData.COLUMN_CLOSE));
        bar.maxPrice = LongNum.fromRawValue(csv.getPrice(ExchangeableData.COLUMN_HIGH));
        bar.minPrice = LongNum.fromRawValue(csv.getPrice(ExchangeableData.COLUMN_LOW));
        bar.volume = LongNum.fromRawValue(PriceUtil.price2long(csv.getInt(ExchangeableData.COLUMN_VOLUME)));
        bar.amount = LongNum.fromRawValue(csv.getPrice(ExchangeableData.COLUMN_TURNOVER));
        bar.openInterest = csv.getLong(ExchangeableData.COLUMN_OPENINT);
        bar.mktAvgPrice = LongNum.fromRawValue(csv.getPrice(ExchangeableData.COLUMN_MKTAVG));
        bar.avgPrice = LongNum.fromRawValue(csv.getPrice(ExchangeableData.COLUMN_AVG));

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
        csvWriter.set(ExchangeableData.COLUMN_TURNOVER, getAmount().toString());
        csvWriter.set(ExchangeableData.COLUMN_OPENINT, ""+getOpenInterest());
        csvWriter.set(ExchangeableData.COLUMN_INDEX, ""+index);
    }

}
