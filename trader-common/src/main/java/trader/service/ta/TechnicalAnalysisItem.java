package trader.service.ta;

import trader.service.md.MarketData;

/**
 * 自定义的技术分析项
 */
public interface TechnicalAnalysisItem<T> {

    public boolean onTick(MarketData tick);

    public T get();
}
