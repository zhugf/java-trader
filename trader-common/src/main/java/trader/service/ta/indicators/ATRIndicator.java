package trader.service.ta.indicators;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.MMAIndicator;
import org.ta4j.core.num.Num;

/**
 * Average true range indicator.
 * <p/>
 */
public class ATRIndicator extends CachedIndicator<Num> {

    private final MMAIndicator averageTrueRangeIndicator;

    public ATRIndicator(BarSeries series, int barCount) {
        super(series);
        this.averageTrueRangeIndicator = new MMAIndicator(new TRIndicator(series), barCount);
    }

    @Override
    protected Num calculate(int index) {
        return averageTrueRangeIndicator.getValue(index);
    }
}
