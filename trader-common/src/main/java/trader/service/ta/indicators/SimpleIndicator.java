package trader.service.ta.indicators;

import java.util.ArrayList;
import java.util.List;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

import trader.service.ta.FutureBar;

/**
 * 多周期序列变量, 如OHLC
 */
public class SimpleIndicator implements Indicator<Num> {

    private BarSeries barSeries;

    private List<Num> values = new ArrayList<>();

    private int beginIndex;

    public SimpleIndicator(BarSeries timeSeries, List<Num> values) {
        this.barSeries = timeSeries;
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
    public BarSeries getBarSeries() {
        return barSeries;
    }

    @Override
    public Num numOf(Number number) {
        return barSeries.numOf(number);
    }

    public static interface BarValueGetter{
        public Num getValue(FutureBar bar);
    }

    public static SimpleIndicator createFromSeries(BarSeries series, BarValueGetter valueGetter) {
        int beginIndex = series.getBeginIndex();
        int endIndex = series.getEndIndex();
        List<Num> values = new ArrayList<>(endIndex-beginIndex+1);
        for(int i=beginIndex; i<=endIndex; i++) {
            FutureBar bar = (FutureBar)series.getBar(i);
            values.add(valueGetter.getValue(bar));
        }
        return new SimpleIndicator(series, values);
    }

    @Override
    public String toString() {
        return getValue().toString();
    }

}
