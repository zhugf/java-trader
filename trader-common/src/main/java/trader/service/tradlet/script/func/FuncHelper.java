package trader.service.tradlet.script.func;

import java.util.ArrayList;
import java.util.List;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

import trader.common.util.ConversionUtil;
import trader.service.tradlet.script.GroovyIndicatorValue;

public class FuncHelper {

    public static Number obj2number(Object obj) {
        Number result = 0;
        if ( obj instanceof GroovyIndicatorValue ) {
            GroovyIndicatorValue indicatorValue = (GroovyIndicatorValue)obj;
            Indicator<Num> indicator = indicatorValue.getIndicator();
            BarSeries series = indicator.getBarSeries();
            result = indicator.getValue(series.getEndIndex()).getDelegate();
        } else if ( obj instanceof Indicator ) {
            Indicator<Num> indicator = (Indicator<Num>)obj;
            BarSeries series = indicator.getBarSeries();
            result = indicator.getValue(series.getEndIndex()).getDelegate();
        } else if ( obj instanceof Number ) {
            result = (Number)obj;
        } else {
            result = ConversionUtil.toDouble(obj, true);
        }
        return result;
    }

    /**
     * 从后向前对齐, 依次遍历
     */
    public static List<Num> forEach(Indicator<Num> i1, Indicator<Num> i2, IndicatorIterator ii) {
        BarSeries s1 = i1.getBarSeries();
        BarSeries s2 = i2.getBarSeries();

        int barCount = Math.max(s1.getBarCount(), s2.getBarCount());
        int bi1 = (barCount-s1.getBarCount());
        int bi2 = (barCount-s2.getBarCount());
        int beginIndex1 = s1.getBeginIndex(), beginIndex2 = s2.getBeginIndex();
        List<Num> values = new ArrayList<>();

        for(int i=0;i<barCount;i++) {
            Num num = null, num2=null;
            if ( i>=bi1 ) {
                num = i1.getValue(i-bi1+beginIndex1);
            }
            if ( i>=bi2 ) {
                num2 = i2.getValue(i-bi2+beginIndex2);
            }
            values.add( ii.apply(num, num2) );
        }
        return values;
    }

    @FunctionalInterface
    public static interface IndicatorIterator {
        Num apply(Num n1, Num n2);
    }
}
