package trader.service.ta;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.ta4j.core.BaseBar;
import org.ta4j.core.num.Num;

import trader.common.util.DateUtil;
import trader.common.util.PriceUtil;
import trader.service.md.MarketData;

/**
 * 附带持仓量的KBar
 */
public class FutureBar extends BaseBar {

    private static final long serialVersionUID = -5989316287411952601L;

    private Num openInterest;
    private Num avgPrice;
    private MarketData beginTick;
    private int index;

    public FutureBar(int index, Duration timePeriod, ZonedDateTime endTime, Num openPrice, Num highPrice, Num lowPrice, Num closePrice, Num volume, Num amount, Num openInterest) {
        super(timePeriod, endTime, openPrice, highPrice, lowPrice, closePrice, volume, amount);
        this.openInterest = openInterest;
        this.index = index;

        long barVol = volume.longValue();
        if ( barVol>0 ) {
            avgPrice =  amount.dividedBy(volume);
        }else {
            avgPrice = closePrice;
        }
    }

    public int getIndex() {
        return index;
    }

    public Num getOpenInterest() {
        return openInterest;
    }

    public Num getAvgPrice() {
        return avgPrice;
    }

    public void update(MarketData tick, LocalDateTime endTime) {
        this.endTime = endTime.atZone(tick.instrumentId.exchange().getZoneId());
        closePrice = new LongNum(tick.lastPrice);
        if ( closePrice.isGreaterThan(maxPrice)) {
            maxPrice = closePrice;
        }
        if ( closePrice.isLessThan(minPrice)) {
            minPrice = closePrice;
        }
        volume = new LongNum(PriceUtil.price2long(tick.volume-beginTick.volume));
        amount = new LongNum(tick.turnover-beginTick.turnover);
        openInterest = new LongNum(PriceUtil.price2long(tick.openInterest));

        long barVol = (tick.volume-beginTick.volume);
        if ( barVol>0 ) {
            avgPrice =  new LongNum((tick.turnover-beginTick.turnover)/barVol);
        }else {
            avgPrice = closePrice;
        }
    }

    public void updateEndTime(ZonedDateTime endTime) {
        this.endTime = endTime;
    }

    @Override
    public String toString() {
        return String.format("{end time: %1s, close price: %2$6.2f, open price: %3$6.2f, min price: %4$6.2f, max price: %5$6.2f, volume: %6$d, openInt: %7$d}",
                endTime.withZoneSameInstant(ZoneId.systemDefault()), closePrice.doubleValue(), openPrice.doubleValue(), minPrice.doubleValue(), maxPrice.doubleValue(), volume.longValue(), openInterest.longValue());
    }

    public static FutureBar create(int barIndex, LocalDateTime barBeginTime, MarketData beginTick, MarketData tick) {
        FutureBar bar = new FutureBar(barIndex, DateUtil.between(barBeginTime, tick.updateTime),
            DateUtil.round(tick.updateTime).atZone(tick.instrumentId.exchange().getZoneId()),
            new LongNum(beginTick.lastPrice),
            new LongNum(Math.max(beginTick.lastPrice, tick.lastPrice)),
            new LongNum(Math.min(beginTick.lastPrice, tick.lastPrice)),
            new LongNum(tick.lastPrice),
            new LongNum(PriceUtil.price2long(tick.volume-beginTick.volume)),
            new LongNum(tick.turnover-beginTick.turnover),
            new LongNum(PriceUtil.price2long(tick.openInterest)));
       bar.beginTick = beginTick;
       return bar;
    }

}
