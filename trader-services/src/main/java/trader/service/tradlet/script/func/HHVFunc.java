package trader.service.tradlet.script.func;

import java.util.ArrayList;
import java.util.List;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.HighestValueIndicator;
import org.ta4j.core.num.Num;

import trader.common.beans.Discoverable;
import trader.service.tradlet.script.GroovyIndicatorValue;
import trader.service.tradlet.script.SimpleIndicator;
import trader.service.tradlet.script.TradletScriptFunction;

@Discoverable(interfaceClass = TradletScriptFunction.class, purpose = "HHV")
public class HHVFunc implements TradletScriptFunction {

    @Override
    public Object invoke(Object[] args) throws Exception {
        GroovyIndicatorValue groovyIndicator = (GroovyIndicatorValue)args[0];
        Indicator<Num> indicator = groovyIndicator.getIndicator();
        int n = ((Number)args[1]).intValue();;
        int barCount = indicator.getTimeSeries().getBarCount();
        int beginIndex = indicator.getTimeSeries().getBeginIndex();

        if ( n<=0 ) { //全局最小值
            Num max = indicator.getValue(beginIndex);
            for(int i=0; i<barCount;i++) {
                Num v = indicator.getValue(i);
                if ( v.isGreaterThan(max)) {
                    max = v;
                }
            }
            List<Num> values = new ArrayList<>();
            for(int i=0;i<barCount;i++) {
                values.add(max);
            }
            return new GroovyIndicatorValue(new SimpleIndicator(indicator.getTimeSeries(), values));
        }else {
            //周期内最小值
            return new GroovyIndicatorValue(new HighestValueIndicator(indicator, n));
        }
    }

}
