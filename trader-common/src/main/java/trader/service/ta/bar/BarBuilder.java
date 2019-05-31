package trader.service.ta.bar;

import trader.common.tick.PriceLevel;
import trader.service.md.MarketData;
import trader.service.ta.LeveledTimeSeries;

public interface BarBuilder {

    LeveledTimeSeries getTimeSeries(PriceLevel level);

    public boolean update(MarketData tick);

}
