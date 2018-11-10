package trader.service.ta;

import java.time.Duration;
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

    public FutureBar(MarketData tick) {
        super(Duration.ofSeconds(0),
            DateUtil.round(tick.updateTime).atZone(tick.instrumentId.exchange().getZoneId()),
            new LongNum(tick.lastPrice),
            new LongNum(tick.lastPrice),
            new LongNum(tick.lastPrice),
            new LongNum(tick.lastPrice),
            LongNum.ZERO,
            LongNum.ZERO
            );
       this.openInterest = new LongNum(tick.openInterest);
       this.beginTick = tick;
    }

    public Num getOpenInterest() {
        return openInterest;
    }

    public void update(MarketData tick) {
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

}
