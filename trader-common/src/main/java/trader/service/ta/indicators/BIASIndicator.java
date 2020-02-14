package trader.service.ta.indicators;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.num.Num;

/**
 * BIAS指标:
 * https://baike.baidu.com/item/%E4%B9%96%E7%A6%BB%E7%8E%87?fromtitle=BIAS%E6%8C%87%E6%A0%87&fromid=6342006#4
 */
public class BIASIndicator extends CachedIndicator<Num> {
    private Indicator<Num> indicator;
    private SMAIndicator sma;
    private Num N100 = numOf(100);

    public BIASIndicator(Indicator<Num> indicator, int barCount) {
        super(indicator);
        this.indicator = indicator;
        sma = new SMAIndicator(indicator, barCount);
    }

    @Override
    protected Num calculate(int index) {
        Num last = indicator.getValue(index);
        Num ma = sma.getValue(index);
        Num result = last.minus(ma).dividedBy(ma).multipliedBy(N100);
        return result;
    }

}
