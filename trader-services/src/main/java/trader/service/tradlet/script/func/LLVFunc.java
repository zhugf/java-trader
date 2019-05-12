package trader.service.tradlet.script.func;

import java.util.ArrayList;
import java.util.List;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.LowestValueIndicator;
import org.ta4j.core.num.Num;

import trader.common.beans.Discoverable;
import trader.service.tradlet.script.GroovyIndicatorValue;
import trader.service.tradlet.script.SimpleIndicator;
import trader.service.tradlet.script.TradletScriptFunction;

@Discoverable(interfaceClass = TradletScriptFunction.class, purpose = "LLV")
public class LLVFunc implements TradletScriptFunction {

    @Override
    public Object invoke(Object[] args) throws Exception {
        GroovyIndicatorValue groovyIndicator = (GroovyIndicatorValue)args[0];
        Indicator<Num> indicator = groovyIndicator.getIndicator();

        int n = ((Number)args[1]).intValue();;
        int barCount = indicator.getTimeSeries().getBarCount();
        int beginIndex = indicator.getTimeSeries().getBeginIndex();

        if ( n<=0 ) { //全局最小值
            Num min = indicator.getValue(beginIndex);
            for(int i=0; i<barCount;i++) {
                Num v = indicator.getValue(i);
                if ( v.isLessThan(min)) {
                    min = v;
                }
            }
            List<Num> values = new ArrayList<>();
            for(int i=0;i<barCount;i++) {
                values.add(min);
            }
            return new GroovyIndicatorValue(new SimpleIndicator(indicator.getTimeSeries(), values));
        }else {
            //周期内最小值
            return new GroovyIndicatorValue(new LowestValueIndicator(indicator, n));
        }
    }

}
