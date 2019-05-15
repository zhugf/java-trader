package trader.service.tradlet.script.func;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.AbsoluteIndicator;
import org.ta4j.core.num.Num;

import trader.common.beans.Discoverable;
import trader.service.tradlet.script.GroovyIndicatorValue;
import trader.service.tradlet.script.TradletScriptFunction;

@Discoverable(interfaceClass = TradletScriptFunction.class, purpose = "ABS")
public class ABSFunc implements TradletScriptFunction {

    @Override
    public Object invoke(Object[] args) throws Exception {
        GroovyIndicatorValue groovyIndicator = (GroovyIndicatorValue)args[0];
        Indicator<Num> indicator = groovyIndicator.getIndicator();

        return new GroovyIndicatorValue(call(indicator));
    }

    public static Indicator<Num> call(Indicator<Num> indicator){
        return new AbsoluteIndicator(indicator);
    }

}
