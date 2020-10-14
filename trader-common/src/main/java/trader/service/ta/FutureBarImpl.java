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
import trader.common.util.JsonUtil;
import trader.common.util.PriceUtil;
import trader.common.util.StringUtil;
import trader.service.md.MarketData;

/**
 * 附带持仓量的KBar
 */
public class FutureBarImpl extends AbsFutureBar {
    private static final long serialVersionUID = -5989316287411952601L;

    private FutureBarImpl(int index, ExchangeableTradingTimes tradingTimes, LocalDateTime beginTime, MarketData openTick, MarketData closeTick, long high, long low) {
        this(index, tradingTimes);
        this.lowPrice = LongNum.fromRawValue(low);
        this.highPrice = LongNum.fromRawValue(high);
        this.openTick = openTick;
        this.closePrice = LongNum.fromRawValue(closeTick.lastPrice);
        this.closeTick = closeTick;
        this.maxTick = openTick;
        this.minTick = openTick;
        setBeginTime(beginTime.atZone(tradingTimes.getInstrument().exchange().getZoneId()));

        if ( index!=0 && openTick==null ) {
            openTick = closeTick;
        }

        if ( index!=0 ) {
            this.openPrice = LongNum.fromRawValue(openTick.lastPrice);
            this.beginAmount = LongNum.fromRawValue(openTick.turnover);
            this.beginVolume = LongNum.valueOf(openTick.volume);
        }else {
            this.openPrice = LongNum.fromRawValue(closeTick.openPrice);
            this.beginAmount = LongNum.ZERO;
            this.beginVolume = LongNum.ZERO;
        }
        if ( openTick!=null ) {
            this.beginOpenInt = openTick.openInterest;
        }else {
            this.beginOpenInt = closeTick.openInterest;
        }
        update(closeTick, closeTick.updateTime);
    }

    private FutureBarImpl(int index, ExchangeableTradingTimes tradingTimes, List<FutureBarImpl> bars) {
        this(index, tradingTimes);
        FutureBarImpl bar0 = bars.get(0), barn = bars.get(bars.size()-1);
        this.mktTimes = bar0.mktTimes;

        setBeginTime(bar0.getBeginTime());

        this.beginAmount = bar0.getBeginAmount();
        this.openPrice = bar0.getOpenPrice();
        this.openTick = bar0.getOpenTick();
        this.beginVolume = bar0.getBeginVolume();
        this.beginOpenInt = bar0.getBeginOpenInt();

        this.endAmount = barn.getEndAmount();
        this.closePrice = barn.getClosePrice();
        this.closeTick = barn.getCloseTick();
        this.endVolume = barn.getEndVolume();
        this.endOpenInt = barn.getEndOpenInt();
        updateEndTime(barn.getEndTime());

        this.mktAvgPrice = barn.getMktAvgPrice();
        this.volume = endVolume.minus(beginVolume);
        this.amount = endAmount.minus(beginAmount);
        this.avgPrice = amount.dividedBy( volume.multipliedBy(LongNum.valueOf(tradingTimes.getInstrument().getVolumeMutiplier())) );
        this.openInt = this.endOpenInt - this.beginOpenInt;

        this.highPrice = bar0.getHighPrice();
        this.maxTick = bar0.getMaxTick();
        this.lowPrice = bar0.getLowPrice();
        this.minTick = bar0.getMaxTick();
        for(int i=1;i<bars.size();i++) {
            FutureBarImpl bar = bars.get(i);
            if ( bar.getHighPrice().isGreaterThan(this.highPrice)) {
                this.highPrice = bar.getHighPrice();
                this.maxTick = bar.getMaxTick();
            }
            if ( bar.getLowPrice().isLessThan(this.lowPrice)) {
                this.lowPrice = bar.getLowPrice();
                this.minTick = bar.getMaxTick();
            }
        }
    }

    private FutureBarImpl(int index, ExchangeableTradingTimes tradingTimes, JsonObject json) {
        this(index, tradingTimes);
        ZoneId zoneId = tradingTimes.getInstrument().exchange().getZoneId();
        setBeginTime(JsonUtil.getPropertyAsDateTime(json, "beginTime").atZone(zoneId));
        updateEndTime(JsonUtil.getPropertyAsDateTime(json, "endTime").atZone(zoneId));

        beginAmount = JsonUtil.getPropertyAsNum(json, "beginAmount");
        beginVolume = JsonUtil.getPropertyAsNum(json, "beginVolume");
        beginOpenInt = json.get("beginOpenInt").getAsLong();

        endAmount = JsonUtil.getPropertyAsNum(json, "endAmount");
        endVolume = JsonUtil.getPropertyAsNum(json, "endVolume");
        endOpenInt = json.get("endOpenInt").getAsLong();

        avgPrice = JsonUtil.getPropertyAsNum(json, "avgPrice");
        mktAvgPrice = JsonUtil.getPropertyAsNum(json, "mktAvgPrice");

        openPrice = JsonUtil.getPropertyAsNum(json, "open");
        closePrice = JsonUtil.getPropertyAsNum(json, "close");
        highPrice = JsonUtil.getPropertyAsNum(json, "max");
        lowPrice = JsonUtil.getPropertyAsNum(json, "min");
        volume = JsonUtil.getPropertyAsNum(json, "volume");
        amount = JsonUtil.getPropertyAsNum(json, "turnover");
        openInt = endOpenInt - beginOpenInt;
    }

    private FutureBarImpl(int index, ExchangeableTradingTimes tradingTimes) {
        this.index = index;
        this.mktTimes = tradingTimes;
    }

    public void update(MarketData tick, LocalDateTime endTime) {
        long priceTick = tick.instrument.getPriceTick(), volMultiplier = tick.instrument.getVolumeMutiplier();
        long newHighestPrice=tick.highestPrice, newLowestPrice=tick.lowestPrice;
        long lastHighestPrice = this.closeTick.highestPrice, lastLowestPrice= this.closeTick.lowestPrice;
        this.closeTick = tick;
        long closePriceRaw = tick.lastPrice, maxPrice=0, minPrice=0, barAvgPrice=0;
        maxPrice = ((LongNum)this.highPrice).rawValue();
        minPrice = ((LongNum)this.lowPrice).rawValue();
        this.closePrice = LongNum.fromRawValue(closePriceRaw);
        this.endAmount = LongNum.fromRawValue(tick.turnover);
        this.endVolume = LongNum.valueOf(tick.volume);
        this.endOpenInt = (tick.openInterest);
        this.openInt = this.endOpenInt - this.beginOpenInt;
        this.volume = endVolume.minus(beginVolume);
        this.amount = endAmount.minus(beginAmount);
        if ( this.upperLimit==null ) {
            this.upperLimit = LongNum.fromRawValue(tick.upperLimitPrice);
        }
        if ( this.lowerLimit==null ) {
            this.lowerLimit = LongNum.fromRawValue(tick.lowerLimitPrice);
        }
        int barVol = volume.intValue();
        if ( barVol!=0 ) {
            barAvgPrice = ((LongNum)amount).rawValue()/(barVol*volMultiplier);
        } else {
            barAvgPrice = tick.lastPrice;
        }

        if ( this.closePrice.isGreaterThan(this.highPrice)) {
            this.highPrice = this.closePrice;
            this.maxTick = closeTick;
        }
        if ( this.closePrice.isLessThan(this.lowPrice)) {
            this.lowPrice = this.closePrice;
            this.minTick = closeTick;
        }

        if ( newHighestPrice!=lastHighestPrice && PriceUtil.isValidPrice(newHighestPrice) ) {
            maxPrice = newHighestPrice;
            this.maxTick = tick;
        }
        if ( newLowestPrice!=lastLowestPrice && PriceUtil.isValidPrice(newLowestPrice) ) {
            minPrice = newLowestPrice;
            this.minTick = tick;
        }

        if ( barAvgPrice> maxPrice) {
            maxPrice = ((barAvgPrice+priceTick/2)/priceTick)*priceTick;
        }
        if ( barAvgPrice<minPrice) {
            minPrice = (barAvgPrice/priceTick)*priceTick;
        }
        this.avgPrice = LongNum.fromRawValue(barAvgPrice);
        mktAvgPrice = LongNum.fromRawValue(tick.averagePrice);
        if ( barAvgPrice>maxPrice || barAvgPrice<minPrice ){
            //System.out.println("avg: "+avgPrice.toString()+", max: "+maxPrice.toString()+", min: "+minPrice.toString());
        }
        updateEndTime( endTime.atZone(tick.instrument.exchange().getZoneId()));
    }

    public static FutureBarImpl fromJson(Exchangeable instrument, JsonElement jsonElem) {
        JsonObject json = jsonElem.getAsJsonObject();
        int index = json.get("index").getAsInt();
        LocalDate tradingDay = JsonUtil.getPropertyAsDate(json, "tradingDay");
        ExchangeableTradingTimes mktTimes = instrument.exchange().getTradingTimes(instrument, tradingDay);
        return new FutureBarImpl(index, mktTimes, json);
    }

    public static FutureBarImpl fromTicks(int barIndex, ExchangeableTradingTimes tradingTimes, LocalDateTime barBeginTime, MarketData beginTick, MarketData tick, long high, long low) {
        return new FutureBarImpl(barIndex, tradingTimes, barBeginTime, beginTick, tick, high, low);
    }

    public static FutureBarImpl fromBars(int barIndex, ExchangeableTradingTimes tradingTimes, List<FutureBarImpl> bars) {
        return new FutureBarImpl(barIndex, tradingTimes, bars);
    }

    public static FutureBarImpl fromCSV(CSVDataSet csv, Exchangeable instrument, ExchangeableTradingTimes tradingTimes) {
        ZoneId zoneId = instrument.exchange().getZoneId();

        int colIndex = csv.getColumnIndex(ExchangeableData.COLUMN_INDEX);
        int index = csv.getRowIndex();
        if ( colIndex>=0 ) {
            String barIndexStr = csv.get(colIndex);
            if ( !StringUtil.isEmpty(barIndexStr)) {
                index = ConversionUtil.toInt(barIndexStr);
            }
        }
        FutureBarImpl bar = new FutureBarImpl(index, tradingTimes);
        LocalDateTime beginTime = csv.getDateTime(ExchangeableData.COLUMN_BEGIN_TIME);
        bar.beginTime = beginTime.atZone(zoneId);
        bar.beginVolume = LongNum.fromRawValue(PriceUtil.price2long(csv.getInt(ExchangeableData.COLUMN_BEGIN_VOLUME)));
        bar.beginAmount = LongNum.fromRawValue(csv.getPrice(ExchangeableData.COLUMN_BEGIN_AMOUNT));
        bar.beginOpenInt = csv.getLong(ExchangeableData.COLUMN_BEGIN_OPENINT);
        bar.beginMktTime = tradingTimes.getTradingTime(beginTime);
        bar.endVolume = LongNum.fromRawValue(PriceUtil.price2long(csv.getInt(ExchangeableData.COLUMN_BEGIN_VOLUME)));
        bar.endAmount = LongNum.fromRawValue(csv.getPrice(ExchangeableData.COLUMN_END_AMOUNT));
        bar.beginOpenInt = csv.getLong(ExchangeableData.COLUMN_BEGIN_OPENINT);

        bar.openPrice = LongNum.fromRawValue(csv.getPrice(ExchangeableData.COLUMN_OPEN));
        bar.closePrice = LongNum.fromRawValue(csv.getPrice(ExchangeableData.COLUMN_CLOSE));
        bar.highPrice = LongNum.fromRawValue(csv.getPrice(ExchangeableData.COLUMN_HIGH));
        bar.lowPrice = LongNum.fromRawValue(csv.getPrice(ExchangeableData.COLUMN_LOW));
        bar.volume = LongNum.fromRawValue(PriceUtil.price2long(csv.getInt(ExchangeableData.COLUMN_VOLUME)));
        bar.amount = LongNum.fromRawValue(csv.getPrice(ExchangeableData.COLUMN_AMOUNT));
        bar.endOpenInt = csv.getLong(ExchangeableData.COLUMN_END_OPENINT);
        bar.mktAvgPrice = LongNum.fromRawValue(csv.getPrice(ExchangeableData.COLUMN_MKTAVG));
        bar.avgPrice = LongNum.fromRawValue(csv.getPrice(ExchangeableData.COLUMN_AVG));
        bar.openInt = bar.endOpenInt - bar.beginOpenInt;
        if ( csv.getColumnIndex(ExchangeableData.COLUMN_UPPER_LIMIT)>0 ){
            bar.upperLimit = LongNum.fromRawValue(csv.getPrice(ExchangeableData.COLUMN_UPPER_LIMIT));
            bar.lowerLimit = LongNum.fromRawValue(csv.getPrice(ExchangeableData.COLUMN_LOWER_LIMIT));
        }
        bar.updateEndTime( csv.getDateTime(ExchangeableData.COLUMN_END_TIME).atZone(zoneId));
        return bar;
    }

    /**
     * 从DAY转换过来
     */
    public static FutureBarImpl fromDayCSV(CSVDataSet csv, Exchangeable instrument) {
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
        FutureBarImpl bar = new FutureBarImpl(index, tradingTimes);
        bar.beginTime = tradingTimes.getMarketOpenTime().atZone(zoneId);
        bar.beginAmount = LongNum.ZERO;
        bar.beginOpenInt = csv.getLong(ExchangeableData.COLUMN_BEGIN_OPENINT);
        bar.endTime = tradingTimes.getMarketCloseTime().atZone(zoneId);
        bar.volume = LongNum.fromRawValue(PriceUtil.price2long(csv.getInt(ExchangeableData.COLUMN_VOLUME)));
        bar.endVolume = bar.volume;
        bar.amount = LongNum.fromRawValue(csv.getPrice(ExchangeableData.COLUMN_AMOUNT));
        bar.endAmount = bar.amount;

        bar.openPrice = LongNum.fromRawValue(csv.getPrice(ExchangeableData.COLUMN_OPEN));
        bar.closePrice = LongNum.fromRawValue(csv.getPrice(ExchangeableData.COLUMN_CLOSE));
        bar.highPrice = LongNum.fromRawValue(csv.getPrice(ExchangeableData.COLUMN_HIGH));
        bar.lowPrice = LongNum.fromRawValue(csv.getPrice(ExchangeableData.COLUMN_LOW));
        bar.volume = LongNum.fromRawValue(PriceUtil.price2long(csv.getDouble(ExchangeableData.COLUMN_VOLUME)));
        bar.amount = LongNum.fromRawValue(csv.getPrice(ExchangeableData.COLUMN_AMOUNT));
        bar.endOpenInt = csv.getLong(ExchangeableData.COLUMN_END_OPENINT);
        bar.openInt = bar.endOpenInt-bar.beginOpenInt;
        bar.mktAvgPrice = bar.closePrice;
        bar.avgPrice = bar.closePrice;

        bar.timePeriod = DateUtil.between(bar.beginTime.toLocalDateTime(), bar.endTime.toLocalDateTime());
        return bar;
    }

    public void save(CSVWriter csvWriter) {
        csvWriter.set(ExchangeableData.COLUMN_BEGIN_TIME, DateUtil.date2str(getBeginTime().toLocalDateTime()));
        csvWriter.set(ExchangeableData.COLUMN_END_TIME, DateUtil.date2str(getEndTime().toLocalDateTime()));
        csvWriter.set(ExchangeableData.COLUMN_OPEN, getOpenPrice().toString());
        csvWriter.set(ExchangeableData.COLUMN_HIGH, getHighPrice().toString());
        csvWriter.set(ExchangeableData.COLUMN_LOW, getLowPrice().toString());
        csvWriter.set(ExchangeableData.COLUMN_CLOSE, getClosePrice().toString());
        csvWriter.set(ExchangeableData.COLUMN_AVG, getAvgPrice().toString());
        csvWriter.set(ExchangeableData.COLUMN_MKTAVG, getMktAvgPrice().toString());
        csvWriter.set(ExchangeableData.COLUMN_VOLUME, ""+getVolume().longValue());
        csvWriter.set(ExchangeableData.COLUMN_AMOUNT, getAmount().toString());

        csvWriter.set(ExchangeableData.COLUMN_BEGIN_VOLUME, ""+getBeginVolume().longValue());
        csvWriter.set(ExchangeableData.COLUMN_BEGIN_AMOUNT, getBeginAmount().toString());
        csvWriter.set(ExchangeableData.COLUMN_BEGIN_OPENINT, ""+getBeginOpenInt());

        csvWriter.set(ExchangeableData.COLUMN_END_VOLUME, ""+getEndVolume().longValue());
        csvWriter.set(ExchangeableData.COLUMN_END_AMOUNT, getEndAmount().toString());
        csvWriter.set(ExchangeableData.COLUMN_END_OPENINT, ""+getEndOpenInt());
        csvWriter.set(ExchangeableData.COLUMN_INDEX, ""+index);
        if ( upperLimit!=null ) {
            csvWriter.set(ExchangeableData.COLUMN_UPPER_LIMIT, upperLimit.toString());
        }
        if ( lowerLimit!=null ) {
            csvWriter.set(ExchangeableData.COLUMN_LOWER_LIMIT, lowerLimit.toString());
        }
    }

    public void saveDay(CSVWriter csvWriter) {
        csvWriter.set(ExchangeableData.COLUMN_DATE, DateUtil.date2str(mktTimes.getTradingDay()));
        csvWriter.set(ExchangeableData.COLUMN_OPEN, getOpenPrice().toString());
        csvWriter.set(ExchangeableData.COLUMN_HIGH, getHighPrice().toString());
        csvWriter.set(ExchangeableData.COLUMN_LOW, getLowPrice().toString());
        csvWriter.set(ExchangeableData.COLUMN_CLOSE, getClosePrice().toString());
        csvWriter.set(ExchangeableData.COLUMN_VOLUME, ""+getVolume().longValue());
        csvWriter.set(ExchangeableData.COLUMN_AMOUNT, getAmount().toString());
        csvWriter.set(ExchangeableData.COLUMN_BEGIN_OPENINT, ""+getBeginOpenInt());
        csvWriter.set(ExchangeableData.COLUMN_END_OPENINT, ""+getEndOpenInt());
    }

}
