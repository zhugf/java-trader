package trader.service.ta.indicators;

import java.time.LocalDate;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

import trader.common.exchangeable.Exchangeable;
import trader.service.ta.FutureBar;
import trader.service.ta.LongNum;

/**
 * 日成交量 indicator, 自动单边双边调整
 */
public class DayVolumeIndicator extends CachedIndicator<Num> {

    public DayVolumeIndicator(BarSeries series) {
        super(series);
    }

    @Override
    protected Num calculate(int index) {
        FutureBar bar = (FutureBar)getBarSeries().getBar(index);
        long volume = bar.getVolume().longValue();
        Exchangeable instrument = bar.getTradingTimes().getInstrument();
        LocalDate tradingDay = bar.getTradingTimes().getTradingDay();
        volume = instrument.exchange().adjustOpenInt(instrument, tradingDay, volume, false);
        return LongNum.valueOf(volume);
    }

}