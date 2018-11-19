package trader.service.tradlet;

import trader.common.beans.Lifecycle;
import trader.service.md.MarketData;
import trader.service.ta.LeveledTimeSeries;

/**
 * 交易策略实例, 可以动态加载和释放.
 * <BR>Tradlet实例必须属于某个TradletGroup关联, 在初始化时, 从BeansContainer.getBean(TradletGroup.class)方式拿到
 */
public interface Tradlet extends Lifecycle {

    /**
     * 由实现类定制的元数据
     */
    public TradletMetadata getMetadata();

    /**
     * 当有新的行情数据来的时候
     */
    public void onMarketData(MarketData marketData);

    /**
     * 当有新的分钟线产生
     * @param series
     */
    public void onNewBar(LeveledTimeSeries series);

}
