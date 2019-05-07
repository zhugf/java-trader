package trader.service.tradlet.script.func;

import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

import trader.common.beans.Discoverable;
import trader.service.tradlet.script.TradletScriptFunction;

@Discoverable(interfaceClass = TradletScriptFunction.class, purpose = "ABS")
public class ABSFunc implements TradletScriptFunction {

    @Override
    public Object invoke(Object[] args) throws Exception {
        Indicator<Num> indicator = (Indicator<Num>)args[0];

        return null;
    }

}
