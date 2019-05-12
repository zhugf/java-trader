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
        Number value = ((Number)args[1]).intValue();
        TimeSeries series = indicator.getTimeSeries();
        int endIndex = series.getEndIndex();
        Num lastValue = indicator.getValue(endIndex);
        // N-1 <= value && N<value
        boolean result = false;
        if ( endIndex>series.getBeginIndex() ) {
            Num prevValue  =indicator.getValue(endIndex-1);
            result = prevValue.getDelegate().doubleValue()<=value.doubleValue() && lastValue.getDelegate().doubleValue()>value.doubleValue();
        }
        return result;
    }

}
