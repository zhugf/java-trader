package trader.service.ta.indicators;

import java.time.LocalDate;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

import trader.common.exchangeable.Exchangeable;
import trader.service.ta.FutureBar;
import trader.service.ta.LongNum;

/**
 * Average price indicator.
 */
public class BeginOpenIntIndicator extends CachedIndicator<Num> {

    public BeginOpenIntIndicator(BarSeries series) {
        super(series);
    }

    @Override
    protected Num calculate(int index) {
        FutureBar bar = (FutureBar)getBarSeries().getBar(index);
        long openInt = bar.getBeginOpenInt();
        Exchangeable instrument = bar.getTradingTimes().getInstrument();
        LocalDate tradingDay = bar.getTradingTimes().getTradingDay();
        openInt = instrument.exchange().adjustOpenInt(instrument, tradingDay, openInt);
        return LongNum.valueOf(openInt);
    }
}
