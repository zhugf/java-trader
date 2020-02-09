package trader.service.tradlet.script.func;

import java.util.ArrayList;
import java.util.List;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

import trader.common.beans.Discoverable;
import trader.service.ta.indicators.SimpleIndicator;
import trader.service.tradlet.script.GroovyIndicatorValue;
import trader.service.tradlet.script.TradletScriptFunction;

@Discoverable(interfaceClass = TradletScriptFunction.class, purpose = "MAX")
public class MAXFunc implements TradletScriptFunction {

    @Override
    public Object invoke(Object[] args) throws Exception {
        GroovyIndicatorValue groovyIndicator = (GroovyIndicatorValue)args[0];
        Indicator<Num> indicator = groovyIndicator.getIndicator();

        BarSeries series = indicator.getBarSeries();
        List<Num> values = new ArrayList<>();
        Object o2 = args[1];
        if ( o2 instanceof GroovyIndicatorValue ) {
            //序列vs序列
            Indicator<Num> indicator2 = ((GroovyIndicatorValue)o2).getIndicator();
            values = FuncHelper.forEach(indicator, indicator2, (Num num, Num num2)->{
                if ( num==null ) {
                    return num2;
                } else if ( num2==null ) {
                    return num;
                } else {
                    return num.max(num2);
                }
            });
        } else {
            Num n2 = series.numOf( FuncHelper.obj2number(o2) );
            //序列vs单值
            for(int i=series.getBeginIndex(); i<=series.getEndIndex();i++) {
                Num n = indicator.getValue(i);
                values.add(n.max(n2));
            }
        }
        return new GroovyIndicatorValue(new SimpleIndicator(series, values));
    }

}
