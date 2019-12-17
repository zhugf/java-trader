package trader.service.ta.indicators;

import org.ta4j.core.Bar;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

import trader.service.ta.LongNum;

/**
 * True range indicator.
 * <p/>
 */
public class TRIndicator extends CachedIndicator<Num> {

    public TRIndicator(TimeSeries series) {
        super(series);
    }

    @Override
    protected Num calculate(int index) {
        if ( index==0 ) {
            return LongNum.ZERO;
        }
        Bar bar = getTimeSeries().getBar(index);
        Bar bar_1 = getTimeSeries().getBar(index-1);
        Num ts = bar.getMaxPrice().minus(bar.getMinPrice());
        Num ys = index == 0 ? numOf(0) : bar.getMaxPrice().minus(bar_1.getClosePrice());
        Num yst = index == 0 ? numOf(0) : bar_1.getClosePrice().minus(bar.getMinPrice());
        return ts.abs().max(ys.abs()).max(yst.abs());
    }
}
