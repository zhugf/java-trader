package trader.service.md;

import trader.common.exchangeable.MarketTimeStage;

/**
 * MarketData 回调函数
 */
public interface MarketDataListener {

    public void onMarketData(MarketData marketData, MarketTimeStage mtStage);

}
