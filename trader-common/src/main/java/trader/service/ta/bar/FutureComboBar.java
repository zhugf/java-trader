package trader.service.ta.bar;

import trader.service.md.MarketData;
import trader.service.ta.AbsFutureBar;

/**
 * 套利用KBar
 */
public class FutureComboBar extends AbsFutureBar {

//    public ArbitrageBar(int )

    @Override
    public MarketData getOpenTick() {
        return null;
    }
    @Override
    public MarketData getCloseTick() {
        return null;
    }
    @Override
    public MarketData getMaxTick() {
        return null;
    }
    @Override
    public MarketData getMinTick() {
        return null;
    }

}
