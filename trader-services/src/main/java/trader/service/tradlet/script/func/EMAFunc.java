package trader.service.tradlet.script.func;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.num.Num;

import trader.common.beans.Discoverable;
import trader.service.tradlet.script.GroovyIndicatorValue;
import trader.service.tradlet.script.TradletScriptFunction;

@Discoverable(interfaceClass = TradletScriptFunction.class, purpose = "EMA")
public class EMAFunc implements TradletScriptFunction {

    @Override
    public Object invoke(Object[] args) throws Exception {
        GroovyIndicatorValue groovyIndicator = (GroovyIndicatorValue)args[0];
        Indicator<Num> indicator = groovyIndicator.getIndicator();
        int barCount = ((Number)args[1]).intValue();

        return new GroovyIndicatorValue(new EMAIndicator(indicator, barCount));
    }

}
