package trader.service.ta.indicators;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

import trader.service.ta.FutureBar;

/**
 * Average price indicator.
 */
public class AvgPriceIndicator extends CachedIndicator<Num> {

    public AvgPriceIndicator(BarSeries series) {
        super(series);
    }

    @Override
    protected Num calculate(int index) {
        FutureBar bar = (FutureBar)getBarSeries().getBar(index);
        return bar.getAvgPrice();
    }
}
