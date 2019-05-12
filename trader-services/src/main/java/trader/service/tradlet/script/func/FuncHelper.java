package trader.service.tradlet.script.func;

import org.ta4j.core.Indicator;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.num.Num;

import trader.common.util.ConversionUtil;
import trader.service.tradlet.script.GroovyIndicatorValue;

public class FuncHelper {

    public static Number obj2number(Object obj) {
        Number result = 0;
        if ( obj instanceof GroovyIndicatorValue ) {
            GroovyIndicatorValue indicatorValue = (GroovyIndicatorValue)obj;
            Indicator<Num> indicator = indicatorValue.getIndicator();
            TimeSeries series = indicator.getTimeSeries();
            result = indicator.getValue(series.getEndIndex()).getDelegate();
        } else if ( obj instanceof Indicator ) {
            Indicator<Num> indicator = (Indicator<Num>)obj;
            TimeSeries series = indicator.getTimeSeries();
            result = indicator.getValue(series.getEndIndex()).getDelegate();
        } else if ( obj instanceof Number ) {
            result = (Number)obj;
        } else {
            result = ConversionUtil.toDouble(obj, true);
        }
        return result;
    }

}
