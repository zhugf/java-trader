package trader.service.ta;

import java.time.Duration;
import java.time.ZonedDateTime;

import org.ta4j.core.BaseBar;
import org.ta4j.core.num.Num;

public class FutureBar extends BaseBar {

    private static final long serialVersionUID = -5989316287411952601L;

    private Num openInterest;

    public FutureBar(Duration timePeriod, ZonedDateTime endTime, Num openPrice, Num highPrice, Num lowPrice, Num closePrice, Num volume, Num amount, Num openInterest) {
        super(timePeriod, endTime, openPrice, highPrice, lowPrice, closePrice, volume, amount);
        this.openInterest = openInterest;
    }

    public Num getOpenInterest() {
        return openInterest;
    }

    public void setOpenInterest(Num openInterest) {
        this.openInterest = openInterest;
    }

}
