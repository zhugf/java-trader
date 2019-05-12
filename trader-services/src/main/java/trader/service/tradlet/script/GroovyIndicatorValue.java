package trader.service.tradlet.script;

import java.util.ArrayList;
import java.util.List;

import org.ta4j.core.Indicator;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.num.Num;

import groovy.lang.GroovyObjectSupport;
import trader.service.tradlet.script.func.FuncHelper;

/**
 * Indicator多周期变量在Groovy中的包装
 */
public class GroovyIndicatorValue extends GroovyObjectSupport implements Comparable {

    private Indicator<Num> indicator;

    public GroovyIndicatorValue(Indicator<Num> indicator) {
        this.indicator = indicator;
    }

    public Number getValue() {
        TimeSeries series = indicator.getTimeSeries();
        return indicator.getValue(series.getEndIndex()).getDelegate();
    }

    public Indicator<Num> getIndicator(){
        return indicator;
    }

    public int intValue() {
        return getValue().intValue();
    }

    public long longValue() {
        return getValue().longValue();
    }

    public float floatValue() {
        return getValue().floatValue();
    }

    public double doubleValue() {
        return getValue().doubleValue();
    }

    @Override
    public String toString() {
        return getValue().toString();
    }

    @Override
    public boolean equals(Object obj) {
        Number num = FuncHelper.obj2number(obj);
        return getValue().equals(num);
    }

    @Override
    public int compareTo(Object o) {
        Number num = FuncHelper.obj2number(o);
        return Double.compare(getValue().doubleValue(), num.doubleValue());
    }

    public GroovyIndicatorValue plus(Object d) {
        return arithmetic(d, 0);
    }

    public GroovyIndicatorValue minus(Object o) {
        return arithmetic(o, 1);
    }

    public GroovyIndicatorValue multiply(Object o) {
        return arithmetic(o, 2);
    }

    public GroovyIndicatorValue div(Object o) {
        return arithmetic(o, 3);
    }

    /**
     * 四则运算
     */
    private GroovyIndicatorValue arithmetic(Object o, int method) {
        TimeSeries series = indicator.getTimeSeries();
        List<Num> values = new ArrayList<>(series.getBarCount());
        if ( o instanceof GroovyIndicatorValue ) {
            //序列+序列
            Indicator<Num> indicator2 = ((GroovyIndicatorValue)o).getIndicator();
            TimeSeries series2 = indicator2.getTimeSeries();
            int barCount = Math.min(series.getBarCount(), series2.getBarCount());
            int beginIndex = series.getBeginIndex()+(series.getBarCount()-barCount);
            int beginIndex2 = series2.getBeginIndex()+(series2.getBarCount()-barCount);
            for(int i=0;i<barCount;i++) {
                Num num = indicator.getValue(i+beginIndex);
                Num num2 = indicator2.getValue(i+beginIndex2);
                values.add(arithmeti(num, num2, method));
            }
        } else {
            //序列+数值
            Num num2 = series.numOf( FuncHelper.obj2number(o) );
            for(int i=series.getBeginIndex(); i<=series.getEndIndex();i++) {
                Num num = indicator.getValue(i);
                values.add(arithmeti(num, num2, method));
            }
        }
        return new GroovyIndicatorValue(new SimpleIndicator(series, values));
    }

    private static Num arithmeti(Num num, Num num2,int method) {
        switch(method) {
        case 0:
            return num.plus(num2);
        case 1:
            return num.minus(num2);
        case 2:
            return num.multipliedBy(num2);
        case 3:
            return num.dividedBy(num2);
        default:
            throw new RuntimeException();
        }
    }
}
