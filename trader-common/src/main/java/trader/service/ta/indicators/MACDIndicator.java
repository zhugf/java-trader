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
    private org.ta4j.core.indicators.MACDIndicator diff;
    private EMAIndicator dea;
    private Num num2;

    public MACDIndicator(Indicator<Num> indicator) {
        this(indicator, 12, 26, 9);
    }

    public MACDIndicator(Indicator<Num> indicator, int shortCount, int longCount, int deaCount) {
        super(indicator);
        diff = new org.ta4j.core.indicators.MACDIndicator(indicator, shortCount, longCount);
        dea = new EMAIndicator(diff, deaCount);
        num2 = numOf(2);
    }

    @Override
    protected Num calculate(int index) {
        Num diffVal = diff.getValue(index);
        Num deaVal = dea.getValue(index);
        //2*DIFF-DEA
        Num result = (diffVal.minus(deaVal)).multipliedBy(num2);
        return result;
    }

    public Indicator<Num> getDIFF(){
        return diff;
    }

    public Indicator<Num> getDEA(){
        return dea;
    }

}
