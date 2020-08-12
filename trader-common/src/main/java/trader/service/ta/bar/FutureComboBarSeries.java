package trader.service.ta.bar;

import org.ta4j.core.BaseBarSeries;

import trader.common.exchangeable.Exchangeable;
import trader.common.tick.PriceLevel;
import trader.service.ta.FutureBar;
import trader.service.ta.LeveledBarSeries;

public class FutureComboBarSeries extends BaseBarSeries implements LeveledBarSeries {

    private PriceLevel level;

    public FutureComboBarSeries() {

    }

    @Override
    public Exchangeable getExchangeable() {
        return null;
    }

    @Override
    public PriceLevel getLevel() {
        return level;
    }

    @Override
    public FutureBar getBar2(int i) {
        return null;
    }

}
