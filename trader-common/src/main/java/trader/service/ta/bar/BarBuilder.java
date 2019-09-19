package trader.service.ta.bar;

import trader.common.tick.PriceLevel;
import trader.service.md.MarketData;
import trader.service.ta.LeveledTimeSeries;

public interface BarBuilder {

    public LeveledTimeSeries getTimeSeries(PriceLevel level);

    /**
     * 更新原始TICK
     */
    public boolean update(MarketData tick);
}
