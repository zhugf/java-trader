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
     * 当有新的行情切片来的时候
     */
    public void onTick(MarketData marketData);

    /**
     * 当有新的分钟线产生, 这个函数在新的Bar所在的Tick后调用
     */
    public void onNewBar(LeveledTimeSeries series);

    /**
     * 当新的一秒来到时, 如果上一秒没有行情数据, 会主动调用这个函数.
     */
    public void onNoopSecond();

}
