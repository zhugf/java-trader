package trader.service.ta.indicators;

import org.ta4j.core.Indicator;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.HighestValueIndicator;
import org.ta4j.core.indicators.helpers.LowestValueIndicator;
import org.ta4j.core.indicators.helpers.MaxPriceIndicator;
import org.ta4j.core.indicators.helpers.MinPriceIndicator;
import org.ta4j.core.num.Num;

/**
 * W%R Indicator
 * </p>
 * 计算公式: (HHV(HIGH,M33)-CLOSE)/(HHV(HIGH,M33)-LLV(LOW,M33))*100
 */
public class WilliamsRIndicator extends CachedIndicator<Num> {
    private final Indicator<Num> indicator;

    private final int barCount;

    private Indicator<Num> maxPriceIndicator;

    private Indicator<Num> minPriceIndicator;

    private Num N100;

    public WilliamsRIndicator(TimeSeries timeSeries, int barCount) {
        this(new ClosePriceIndicator(timeSeries), barCount, new MaxPriceIndicator(timeSeries), new MinPriceIndicator(
                timeSeries));
    }

    public WilliamsRIndicator(Indicator<Num> indicator, int barCount,
            MaxPriceIndicator maxPriceIndicator, MinPriceIndicator minPriceIndicator) {
        super(indicator);
        this.indicator = indicator;
        this.barCount = barCount;
        this.maxPriceIndicator = maxPriceIndicator;
        this.minPriceIndicator = minPriceIndicator;
        N100 = numOf(100);
    }

    @Override
    protected Num calculate(int index) {
        HighestValueIndicator highestHigh = new HighestValueIndicator(maxPriceIndicator, barCount);
        LowestValueIndicator lowestMin = new LowestValueIndicator(minPriceIndicator, barCount);

        Num highestHighPrice = highestHigh.getValue(index);
        Num lowestLowPrice = lowestMin.getValue(index);

        return highestHighPrice.minus(indicator.getValue(index))
                .dividedBy(highestHighPrice.minus(lowestLowPrice))
                .multipliedBy(N100);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}