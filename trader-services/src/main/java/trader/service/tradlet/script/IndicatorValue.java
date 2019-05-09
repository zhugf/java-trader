package trader.service.tradlet.script;

import java.util.ArrayList;
import java.util.List;

import org.ta4j.core.Indicator;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.num.Num;

import trader.service.ta.Bar2;

/**
 * 多周期序列变量, 如OHLC
 */
public class IndicatorValue implements Indicator<Num> {

    private TimeSeries timeSeries;

    private List<Num> values = new ArrayList<>();

    private int beginIndex;

    public IndicatorValue(TimeSeries timeSeries, List<Num> values, int beginIndex) {
        this.timeSeries = timeSeries;
        this.values = values;
        this.beginIndex = beginIndex;
    }

    public List<Num> getValues(){
        return values;
    }

    public Num getValue() {
        return values.get(values.size()-1);
    }

    @Override
    public Num getValue(int index) {
        return values.get(index-beginIndex);
    }

    @Override
    public TimeSeries getTimeSeries() {
        return timeSeries;
    }

    @Override
    public Num numOf(Number number) {
        return timeSeries.numOf(number);
    }

    public static interface BarValueGetter{
        public Num getValue(Bar2 bar);
    }

    public static IndicatorValue createFromSeries(TimeSeries series, BarValueGetter valueGetter, int beginIndex, int endIndex) {
        List<Num> values = new ArrayList<>(endIndex-beginIndex+1);

        for(int i=beginIndex; i<=endIndex; i++) {
            Bar2 bar = (Bar2)series.getBar(i);
            values.add(valueGetter.getValue(bar));
        }
        return new IndicatorValue(series, values, beginIndex);
    }

}
