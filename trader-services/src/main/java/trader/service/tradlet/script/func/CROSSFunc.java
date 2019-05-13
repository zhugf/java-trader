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
        TimeSeries series = indicator.getTimeSeries();

        Object base = args[1];
        boolean result = false;
        if ( base instanceof GroovyIndicatorValue ) {
            Indicator<Num> indicator2 = ((GroovyIndicatorValue)base).getIndicator();
            TimeSeries series2 = indicator2.getTimeSeries();
            int barCount = Math.min(series.getBarCount(), series2.getBarCount());
            int beginIndex = series.getBeginIndex()+(series.getBarCount()-barCount);
            int beginIndex2 = series2.getBeginIndex()+(series2.getBarCount()-barCount);

            boolean lessThan=false, greatThan=false;
            for(int i=0;i<barCount;i++) {
                Num num = indicator.getValue(i+beginIndex);
                Num baseNum = indicator2.getValue(i+beginIndex2);
                if ( num.isLessThanOrEqual(baseNum) ) {
                    lessThan = true;
                }else {
                    greatThan = true;
                }
            }
            result = lessThan && greatThan;
        }else {
            int beginIndex = series.getBeginIndex(), endIndex = series.getEndIndex();
            Num baseNum = series.numOf(FuncHelper.obj2number(base));
            boolean lessThan=false, greatThan=false;
            // N-1 <= value && N<value
            for(int i=series.getBeginIndex(); i<endIndex; i++ ) {
                Num num = indicator.getValue(i);
                if ( num.isLessThanOrEqual(baseNum) ) {
                    lessThan = true;
                }else {
                    greatThan = true;
                }
            }
            result = lessThan && greatThan;
        }
        return result;
    }

}
