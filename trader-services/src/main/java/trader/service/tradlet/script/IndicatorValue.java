package trader.service.tradlet.script;

import java.util.ArrayList;
import java.util.List;

import org.ta4j.core.Indicator;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.num.Num;

/**
 * 多周期序列变量, 如OHLC
 */
public class IndicatorValue implements Indicator<Num> {

    private TimeSeries timeSeries;

    private List<Num> values = new ArrayList<>();

    public IndicatorValue(TimeSeries timeSeries) {
        this.timeSeries = timeSeries;
    }

    public Num getValue() {
        return values.get(values.size()-1);
    }

    @Override
    public Num getValue(int index) {
        return values.get(index-timeSeries.getBeginIndex());
    }

    @Override
    public TimeSeries getTimeSeries() {
        return timeSeries;
    }

    @Override
    public Num numOf(Number number) {
        return timeSeries.numOf(number);
    }

}
