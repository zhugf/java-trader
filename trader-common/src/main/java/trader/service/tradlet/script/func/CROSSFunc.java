package trader.service.tradlet.script.func;

import java.util.ArrayList;
import java.util.List;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

import trader.common.beans.Discoverable;
import trader.service.tradlet.script.GroovyIndicatorValue;
import trader.service.tradlet.script.TradletScriptFunction;

/**
 * TODO 需要重写
 */
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
        BarSeries series = indicator.getBarSeries();
        BarSeries series2 = indicator2.getBarSeries();

        int barCount = Math.min(series.getBarCount(), series2.getBarCount());
        boolean result = false;
        if ( barCount>=2 ) {
            int endIndex = series.getEndIndex(), endIndex2 = series2.getEndIndex();

            int lastCompare = indicator.getValue(endIndex).compareTo(indicator2.getValue(endIndex2));
            int prevCompare = indicator.getValue(endIndex-1).compareTo(indicator2.getValue(endIndex2-1));

            result = prevCompare<=0 && lastCompare>0;
        }
        return result;
    }

    public static boolean call(Indicator<Num> indicator, Number base) {
        boolean result = false;
        BarSeries series = indicator.getBarSeries();

        int beginIndex = series.getBeginIndex(), endIndex = series.getEndIndex();
        if ( series.getBarCount()>=2 ) {
            Num baseNum = series.numOf(base);
            int lastCompare = indicator.getValue(endIndex).compareTo(baseNum);
            int prevCompare = indicator.getValue(endIndex-1).compareTo(baseNum);
            result = prevCompare<=0 && lastCompare>0;
        }
        return result;
    }

    public static boolean call(Number compare, Indicator<Num> indicator2) {
        boolean result = false;
        BarSeries series2 = indicator2.getBarSeries();
        int beginIndex2 = series2.getBeginIndex(), endIndex2 = series2.getEndIndex();
        if ( series2.getBarCount()>=2 ) {
            Num compareNum = series2.numOf(FuncHelper.obj2number(compare));
            int lastCompare = compareNum.compareTo(indicator2.getValue(endIndex2));
            int prevCompare = compareNum.compareTo(indicator2.getValue(endIndex2-1));
            result = prevCompare<=0 && lastCompare>0;
        }
        return result;
    }

    /**
     * 返回交叉的Index列表
     */
    public static List<Integer> getCrossIndexes(Indicator<Num> compare, Indicator<Num> base, boolean upCross){
        BarSeries series = compare.getBarSeries();
        BarSeries series2 = base.getBarSeries();
        int barCount = Math.min(series.getBarCount(), series2.getBarCount());
        int beginIndex = series.getBeginIndex()+(series.getBarCount()-barCount);
        int beginIndex2 = series2.getBeginIndex()+(series2.getBarCount()-barCount);

        List<Integer> result = new ArrayList<>();
        int lastCompareResult = 0;
        for(int i=0;i<barCount;i++) {
            Num num = compare.getValue(i+beginIndex);
            Num baseNum = base.getValue(i+beginIndex2);
            int compareResult = num.compareTo(baseNum);
            if ( upCross ) {
                if ( compareResult>0 && lastCompareResult<=0 ) {
                    result.add(i);
                }
            }else {
                if ( compareResult<0 && lastCompareResult>=0 ) {
                    result.add(i);
                }
            }
            lastCompareResult = compareResult;
        }
        return result;
    }

}
