package trader.service.tactic;

import trader.common.beans.Lifecycle;

/**
 * 交易策略实例, 可以动态加载和释放
 */
public interface Tactic extends Lifecycle {

    /**
     * 元数据
     */
    public TacticMetadata getMetadata();

    /**
     * 当有新的行情数据来的时候
     */
    //public void onMarketData(MarketData marketData);

    /**
     * 当有新的分钟线产生
     */
    //public void onBar(Bar bar);

}
