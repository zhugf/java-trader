package trader.service.tradlet.script.func;

import org.ta4j.core.Indicator;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.num.Num;

import trader.common.beans.Discoverable;
import trader.service.tradlet.script.GroovyIndicatorValue;
import trader.service.tradlet.script.TradletScriptFunction;

@Discoverable(interfaceClass = TradletScriptFunction.class, purpose = "CROSS")
public class CROSSFunc implements TradletScriptFunction {

    @Override
    public Object invoke(Object[] args) throws Exception {
        GroovyIndicatorValue groovyIndicator = (GroovyIndicatorValue)args[0];
        Indicator<Num> indicator = groovyIndicator.getIndicator();
        Object base = args[1];
        boolean result = false;
        if ( base instanceof GroovyIndicatorValue ) {

        }else {
            Number value = FuncHelper.obj2number(base);
            TimeSeries series = indicator.getTimeSeries();
            int endIndex = series.getEndIndex();
            Num lastValue = indicator.getValue(endIndex);
            // N-1 <= value && N<value
            if ( endIndex>series.getBeginIndex() ) {
                Num prevValue  =indicator.getValue(endIndex-1);
                result = prevValue.getDelegate().doubleValue()<=value.doubleValue() && lastValue.getDelegate().doubleValue()>value.doubleValue();
            }
        }
        return result;
    }

}
