package trader.service.ta.indicators;

import java.util.ArrayList;
import java.util.List;

import org.ta4j.core.Indicator;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.num.Num;

import trader.service.ta.Bar2;

/**
 * 多周期序列变量, 如OHLC
 */
public class SimpleIndicator implements Indicator<Num> {

    private TimeSeries timeSeries;

    private List<Num> values = new ArrayList<>();

    private int beginIndex;

    public SimpleIndicator(TimeSeries timeSeries, List<Num> values) {
        this.timeSeries = timeSeries;
        this.values = values;
        this.beginIndex = timeSeries.getBeginIndex();
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

    public static SimpleIndicator createFromSeries(TimeSeries series, BarValueGetter valueGetter) {
        int beginIndex = series.getBeginIndex();
        int endIndex = series.getEndIndex();
        List<Num> values = new ArrayList<>(endIndex-beginIndex+1);
        for(int i=beginIndex; i<=endIndex; i++) {
            Bar2 bar = (Bar2)series.getBar(i);
            values.add(valueGetter.getValue(bar));
        }
        return new SimpleIndicator(series, values);
    }

    @Override
    public String toString() {
        return getValue().toString();
    }

}
