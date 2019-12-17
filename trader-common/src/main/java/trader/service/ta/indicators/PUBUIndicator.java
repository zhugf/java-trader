package trader.service.ta.indicators;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.num.Num;

import trader.service.ta.LongNum;

public class PUBUIndicator extends CachedIndicator<Num> {

    private EMAIndicator ema;
    private SMAIndicator sma2;
    private SMAIndicator sma4;

    public PUBUIndicator(Indicator<Num> indicator, int barCount) {
        super(indicator);
        ema = new EMAIndicator(indicator, barCount);
        sma2 = new SMAIndicator(indicator, barCount*2);
        sma4 = new SMAIndicator(indicator, barCount*4);
    }

    @Override
    protected Num calculate(int index) {
        Num value = ema.getValue(index).plus( sma2.getValue(index) ).plus( sma4.getValue(index) );
        return LongNum.fromNum(value).dividedBy(LongNum.THREE);
    }

}
