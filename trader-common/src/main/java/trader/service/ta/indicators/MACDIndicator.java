package trader.service.ta.indicators;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.num.Num;

/**
 * 真正的MACD计算, ta4j的MACDIndicator只是计算diff
 */
public class MACDIndicator extends CachedIndicator<Num>  {
    private static final long serialVersionUID = -1958323274864913390L;

    private org.ta4j.core.indicators.MACDIndicator diffIndicator;
    private EMAIndicator deaIndicator;
    private Num num2;

    public MACDIndicator(Indicator<Num> indicator) {
        this(indicator, 12, 26, 9);
    }

    public MACDIndicator(Indicator<Num> indicator, int shortCount, int longCount, int deaCount) {
        super(indicator);
        diffIndicator = new org.ta4j.core.indicators.MACDIndicator(indicator, shortCount, longCount);
        deaIndicator = new EMAIndicator(diffIndicator, deaCount);
        num2 = numOf(2);
    }

    @Override
    protected Num calculate(int index) {
        Num diff = diffIndicator.getValue(index);
        Num dea = deaIndicator.getValue(index);
        //2*DIFF-DEA
        Num result = (diff.minus(dea)).multipliedBy(num2);
        return result;
    }

}
