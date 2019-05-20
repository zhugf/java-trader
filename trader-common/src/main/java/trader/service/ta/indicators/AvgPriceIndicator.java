package trader.service.ta.indicators;

import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

import trader.service.ta.Bar2;

/**
 * Average price indicator.
 */
public class AvgPriceIndicator extends CachedIndicator<Num> {

    public AvgPriceIndicator(TimeSeries series) {
        super(series);
    }

    @Override
    protected Num calculate(int index) {
        Bar2 bar = (Bar2)getTimeSeries().getBar(index);
        return bar.getAvgPrice();
    }
}
