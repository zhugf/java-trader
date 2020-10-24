package trader.service.ta.indicators;

import java.time.LocalDate;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

import trader.common.exchangeable.Exchangeable;
import trader.service.ta.FutureBar;
import trader.service.ta.LongNum;

/**
 * OpenInt每日结束时的值, 自动单边双边调整
 */
public class EndOpenIntIndicator extends CachedIndicator<Num> {

    public EndOpenIntIndicator(BarSeries series) {
        super(series);
    }

    @Override
    protected Num calculate(int index) {
        FutureBar bar = (FutureBar)getBarSeries().getBar(index);
        long openInt = bar.getEndOpenInt();
        Exchangeable instrument = bar.getTradingTimes().getInstrument();
        LocalDate tradingDay = bar.getTradingTimes().getTradingDay();
        openInt = instrument.exchange().adjustOpenInt(instrument, tradingDay, openInt, false);
        return LongNum.valueOf(openInt);
    }

}
