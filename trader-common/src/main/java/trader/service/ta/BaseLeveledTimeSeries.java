package trader.service.ta;


import java.util.function.Function;

import org.ta4j.core.BaseTimeSeries;
import org.ta4j.core.num.Num;

import trader.common.tick.PriceLevel;

public class BaseLeveledTimeSeries extends BaseTimeSeries implements LeveledTimeSeries {

    private static final long serialVersionUID = 2904300939512922674L;

    private PriceLevel level;

    public BaseLeveledTimeSeries(String name, PriceLevel level, Function<Number, Num> numFunction) {
        super(name, numFunction);
        this.level = level;
    }

    @Override
    public PriceLevel getLevel() {
        return level;
    }

}
