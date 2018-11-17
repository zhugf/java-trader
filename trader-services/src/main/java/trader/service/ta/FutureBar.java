package trader.service.ta;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.ta4j.core.BaseBar;
import org.ta4j.core.num.Num;

import trader.common.util.DateUtil;
import trader.service.md.MarketData;

/**
 * 附带持仓量的KBar
 */
public class FutureBar extends BaseBar {

    private static final long serialVersionUID = -5989316287411952601L;

    private Num openInterest;
    private MarketData beginTick;

    public FutureBar(Duration timePeriod, ZonedDateTime endTime, Num openPrice, Num highPrice, Num lowPrice, Num closePrice, Num volume, Num amount, Num openInterest) {
        super(timePeriod, endTime, openPrice, highPrice, lowPrice, closePrice, volume, amount);
        this.openInterest = openInterest;
    }

    public Num getOpenInterest() {
        return openInterest;
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
        volume = new LongNum(tick.volume-beginTick.volume);
        amount = new LongNum(tick.turnover-beginTick.turnover);
        openInterest = new LongNum(tick.openInterest);
    }

    @Override
    public String toString() {
        return String.format("{end time: %1s, close price: %2$f, open price: %3$f, min price: %4$f, max price: %5$f, volume: %6$f, openInt: %7$d}",
                endTime.withZoneSameInstant(ZoneId.systemDefault()), closePrice.doubleValue(), openPrice.doubleValue(), minPrice.doubleValue(), maxPrice.doubleValue(), volume.doubleValue(), openInterest.longValue());
    }

    public static FutureBar create(MarketData tick) {
        FutureBar bar = new FutureBar(Duration.ofMillis(1),
            DateUtil.round(tick.updateTime).plusNanos(1000000).atZone(tick.instrumentId.exchange().getZoneId()),
            new LongNum(tick.lastPrice),
            new LongNum(tick.lastPrice),
            new LongNum(tick.lastPrice),
            new LongNum(tick.lastPrice),
            LongNum.ZERO,
            LongNum.ZERO,
            new LongNum(tick.openInterest));
       bar.beginTick = tick;
       return bar;
    }

}
