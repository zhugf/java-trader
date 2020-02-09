package trader.service.ta.indicators;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

import trader.service.ta.LongNum;

/**
 * True range indicator.
 * <p/>
 */
public class TRIndicator extends CachedIndicator<Num> {

    public TRIndicator(BarSeries series) {
        super(series);
    }

    @Override
    protected Num calculate(int index) {
        if ( index==0 ) {
            return LongNum.ZERO;
        }
        Bar bar = getBarSeries().getBar(index);
        Bar bar_1 = getBarSeries().getBar(index-1);
        Num ts = bar.getHighPrice().minus(bar.getLowPrice());
        Num ys = index == 0 ? numOf(0) : bar.getHighPrice().minus(bar_1.getClosePrice());
        Num yst = index == 0 ? numOf(0) : bar_1.getClosePrice().minus(bar.getLowPrice());
        return ts.abs().max(ys.abs()).max(yst.abs());
    }
}
