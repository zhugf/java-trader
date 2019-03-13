package trader.service.ta;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.ta4j.core.num.Num;

import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableData;
import trader.common.util.CSVDataSet;
import trader.common.util.CSVWriter;
import trader.common.util.ConversionUtil;
import trader.common.util.DateUtil;
import trader.common.util.PriceUtil;
import trader.common.util.StringUtil;
import trader.service.md.MarketData;

/**
 * 附带持仓量的KBar
 */
public class FutureBar implements Bar2 {
    private static final long serialVersionUID = -5989316287411952601L;

    protected Num avgPrice;
    protected MarketData openTick;
    protected MarketData closeTick;
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
    /** Trade count */
    protected int trades = 0;

    protected Num mktAvgPrice;
    protected long openInterest;

    private FutureBar(int index, LocalDateTime beginTime, MarketData openTick, MarketData closeTick, long high, long low) {
        this.index = index;
        this.beginTime = beginTime.atZone(closeTick.instrumentId.exchange().getZoneId());
        this.minPrice = new LongNum(low);
        this.maxPrice = new LongNum(high);
        this.openTick = openTick;
        if ( openTick!=null ) {
            this.openPrice = new LongNum(openTick.lastPrice);
        }else {
            this.openPrice = new LongNum(closeTick.openPrice);
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

    public void update(MarketData tick, LocalDateTime endTime) {
        MarketData lastTick = this.closeTick;
        this.closeTick = tick;
        if ( lastTick==null ) {
            lastTick = tick;
        }
        this.endTime = endTime.atZone(tick.instrumentId.exchange().getZoneId());
        closePrice = new LongNum(tick.lastPrice);
        long barVol=0;
        if ( openTick!=null ) {
            volume = new LongNum(PriceUtil.price2long(tick.volume-openTick.volume));
            amount = new LongNum(tick.turnover-openTick.turnover);
            barVol = (tick.volume-openTick.volume);
            if ( barVol>0 ) {
                avgPrice = new LongNum((tick.turnover-openTick.turnover)/(barVol*tick.instrumentId.getVolumeMutiplier()));
            } else {
                avgPrice = closePrice;
            }
            if ( tick.highestPrice!=lastTick.highestPrice ) {
                maxPrice = new LongNum(tick.highestPrice);
            }else if ( closePrice.isGreaterThan(maxPrice)) {
                maxPrice = closePrice;
            }
            if ( tick.lowestPrice!=lastTick.lowestPrice ) {
                minPrice = new LongNum(tick.lowestPrice);
            } else if ( closePrice.isLessThan(minPrice)) {
                minPrice = closePrice;
            }
        }else {
            volume = new LongNum(PriceUtil.price2long(tick.volume));
            amount = new LongNum(tick.turnover);
            barVol = tick.volume;
            avgPrice = new LongNum(tick.averagePrice);
            maxPrice = new LongNum(tick.highestPrice);
            minPrice = new LongNum(tick.lowestPrice);
        }
        openInterest = (tick.openInterest);

        while ( avgPrice.isGreaterThan(maxPrice)) {
            maxPrice = maxPrice.plus(new LongNum(tick.instrumentId.getPriceTick()));
        }
        while ( avgPrice.isLessThan(minPrice)) {
            minPrice = minPrice.minus(new LongNum(tick.instrumentId.getPriceTick()));
        }
        if ( avgPrice.getDelegate().longValue()>(maxPrice.getDelegate().longValue())
              || avgPrice.getDelegate().longValue()<(minPrice.getDelegate().longValue()) )
        {
            //System.out.println("avg: "+avgPrice.toString()+", max: "+maxPrice.toString()+", min: "+minPrice.toString());
        }
        mktAvgPrice = new LongNum(tick.averagePrice);
        timePeriod = DateUtil.between(beginTime.toLocalDateTime(), tick.updateTime);
    }

    public void updateEndTime(ZonedDateTime endTime) {
        this.endTime = endTime;
    }

    @Override
    public String toString() {
        return String.format("{end time: %1s, close price: %2$6.2f, open price: %3$6.2f, min price: %4$6.2f, max price: %5$6.2f, volume: %6$d, openInt: %7$d}",
                endTime.withZoneSameInstant(ZoneId.systemDefault()), closePrice.doubleValue(), openPrice.doubleValue(), minPrice.doubleValue(), maxPrice.doubleValue(), volume.longValue(), openInterest);
    }

    public static FutureBar create(int barIndex, LocalDateTime barBeginTime, MarketData beginTick, MarketData tick, long high, long low) {
        FutureBar bar = null;
        bar = new FutureBar(barIndex, barBeginTime, beginTick, tick, high, low);
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
        bar.openPrice = new LongNum(csv.getPrice(ExchangeableData.COLUMN_OPEN));
        bar.closePrice = new LongNum(csv.getPrice(ExchangeableData.COLUMN_CLOSE));
        bar.maxPrice = new LongNum(csv.getPrice(ExchangeableData.COLUMN_HIGH));
        bar.minPrice = new LongNum(csv.getPrice(ExchangeableData.COLUMN_LOW));
        bar.volume = new LongNum(PriceUtil.price2long(csv.getInt(ExchangeableData.COLUMN_VOLUME)));
        bar.amount = new LongNum(csv.getPrice(ExchangeableData.COLUMN_TURNOVER));
        bar.openInterest = csv.getLong(ExchangeableData.COLUMN_OPENINT);
        bar.mktAvgPrice = new LongNum(csv.getPrice(ExchangeableData.COLUMN_MKTAVG));
        bar.avgPrice = new LongNum(csv.getPrice(ExchangeableData.COLUMN_AVG));

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
