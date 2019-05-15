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
        Object compare = args[0];
        Object base = args[1];

        boolean result = false;

        if ( compare instanceof GroovyIndicatorValue && base instanceof GroovyIndicatorValue ) {
            GroovyIndicatorValue groovyIndicator = (GroovyIndicatorValue)args[0];
            Indicator<Num> indicator = groovyIndicator.getIndicator();
            Indicator<Num> indicator2 = ((GroovyIndicatorValue)base).getIndicator();
            result = call(indicator, indicator2);
        } else if ( compare instanceof GroovyIndicatorValue ){
            GroovyIndicatorValue groovyIndicator = (GroovyIndicatorValue)args[0];
            Indicator<Num> indicator = groovyIndicator.getIndicator();
            return call(indicator, FuncHelper.obj2number(base));
        } else if ( base instanceof GroovyIndicatorValue ) {
            Indicator<Num> indicator2 = ((GroovyIndicatorValue)base).getIndicator();
            result = call(FuncHelper.obj2number(compare), indicator2);
        }
        return result;
    }

    public static boolean call(Indicator<Num> indicator, Indicator<Num> indicator2) {
        boolean result = false;

        TimeSeries series = indicator.getTimeSeries();

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

        return result;
    }

    public static boolean call(Indicator<Num> indicator, Number base) {
        boolean result = false;
        TimeSeries series = indicator.getTimeSeries();

        int beginIndex = series.getBeginIndex(), endIndex = series.getEndIndex();
        Num baseNum = series.numOf(base);
        boolean lessThan=false, greatThan=false;
        // N-1 <= value && N<value
        for(int i=series.getBeginIndex(); i<=endIndex; i++ ) {
            Num num = indicator.getValue(i);
            if ( num.isLessThanOrEqual(baseNum) ) {
                lessThan = true;
            }else {
                greatThan = true;
            }
        }
        result = lessThan && greatThan;
        return result;
    }

    public static boolean call(Number compare, Indicator<Num> indicator2) {
        boolean result = false;
        TimeSeries series2 = indicator2.getTimeSeries();
        int beginIndex2 = series2.getBeginIndex(), endIndex2 = series2.getEndIndex();

        Num compareNum = series2.numOf(FuncHelper.obj2number(compare));

        boolean lessThan=false, greatThan=false;
        for(int i=series2.getBeginIndex(); i<=endIndex2; i++ ) {
            Num num = indicator2.getValue(i);
            if ( compareNum.isLessThan(num) ) {
                lessThan = true;
            }else {
                greatThan = true;
            }
        }
        result = lessThan && greatThan;

        return result;
    }

}
