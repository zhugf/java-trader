package trader.service.tradlet.script.func;

import java.util.ArrayList;
import java.util.List;

import org.ta4j.core.Indicator;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.num.Num;

import trader.common.beans.Discoverable;
import trader.service.ta.LongNum;
import trader.service.tradlet.script.GroovyIndicatorValue;
import trader.service.tradlet.script.SimpleIndicator;
import trader.service.tradlet.script.TradletScriptFunction;

@Discoverable(interfaceClass = TradletScriptFunction.class, purpose = "REF")
public class REFFunc implements TradletScriptFunction {

    @Override
    public Object invoke(Object[] args) throws Exception {
        GroovyIndicatorValue groovyIndicator = (GroovyIndicatorValue)args[0];
        Indicator<Num> indicator = groovyIndicator.getIndicator();
        int n = FuncHelper.obj2number(args[1]).intValue();;

        TimeSeries series = indicator.getTimeSeries();
        List<Num> values = new ArrayList<>(series.getBarCount());
        int barCount = series.getBarCount();
        n = Math.min(n, barCount);
        for(int i=0;i<n;i++) {
            values.add(LongNum.ZERO);
        }
        if ( n<barCount ) {
            for(int i=0; i<barCount-n; i++) {
                values.add( indicator.getValue(series.getBeginIndex()+i));
            }
        }

        return new GroovyIndicatorValue(new SimpleIndicator(series, values));
    }

}
