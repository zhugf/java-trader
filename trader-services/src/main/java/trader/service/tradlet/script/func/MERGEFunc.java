package trader.service.tradlet.script.func;

import java.util.ArrayList;
import java.util.List;

import org.ta4j.core.Indicator;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.num.Num;

import groovy.lang.Closure;
import trader.common.beans.Discoverable;
import trader.service.tradlet.script.GroovyIndicatorValue;
import trader.service.tradlet.script.SimpleIndicator;
import trader.service.tradlet.script.TradletScriptFunction;

@Discoverable(interfaceClass = TradletScriptFunction.class, purpose = "MERGE")
public class MERGEFunc implements TradletScriptFunction {

    @Override
    public Object invoke(Object[] args) throws Exception {
        Object indicators[] = new Object[args.length-1];
        for(int i=0;i<indicators.length;i++) {
            Object p = args[i];
            if ( p instanceof GroovyIndicatorValue ) {
                indicators[i] = ((GroovyIndicatorValue)p).getIndicator();
            } else {
                indicators[i] = FuncHelper.obj2number(p);
            }
        }
        Closure closure = (Closure)args[args.length-1];
        Indicator<Num> result = call(indicators, closure);
        if ( result!=null ) {
            return new GroovyIndicatorValue(result);
        }else {
            return null;
        }
    }

    public static Indicator<Num> call(Object[] indicators, Closure closure){
        int maxBarCount=0;
        int barCounts[] = new int[indicators.length];
        int beginIndexes[] = new int[indicators.length];
        int adjust[] = new int[indicators.length];
        Number nums[] = new Number[indicators.length];
        TimeSeries timeSeries = null;
        for(int i=0;i<indicators.length;i++) {
            Object indicator = indicators[i];
            if ( indicator instanceof Indicator ) {
                timeSeries = ((Indicator)indicator).getTimeSeries();
                barCounts[i] = timeSeries.getBarCount();
                beginIndexes[i] = timeSeries.getBeginIndex();
                maxBarCount = Math.max(maxBarCount, barCounts[i]);
            } else {
                nums[i] = FuncHelper.obj2number(indicator);
                barCounts[i] = -1;
                adjust[i] = -1;
                beginIndexes[i] = -1;
            }
        }
        for(int i=0;i<indicators.length;i++) {
            if ( barCounts[i]>0 ) {
                adjust[i] = maxBarCount-barCounts[i];
            }
            //使用BarCount最大的TimeSeries
            if ( adjust[i]==0 ) {
                timeSeries = ((Indicator)indicators[i]).getTimeSeries();
            }
        }

        List<Num> values = new ArrayList<>(maxBarCount);
        for(int i=0;i<maxBarCount;i++) {
            for(int j=0;j<indicators.length;j++) {
                if ( adjust[j]>=0 && i>=adjust[j] ) {
                    nums[j] = ((Indicator<Num>)indicators[j]).getValue(i+beginIndexes[j]-adjust[j]).getDelegate();
                }
            }

            Object r = closure.call((Object[])nums);
            values.add( timeSeries.numOf(FuncHelper.obj2number(r)));
        }

        return new SimpleIndicator(timeSeries, values);
    }

}
