package trader.service.tradlet;

import java.util.Set;

import trader.common.tick.PriceLevel;

/**
 * 交易策略的元数据, 附加描述Tradlet所需数据等
 */
public interface TradletMetadata {

    /**
     * 返回感兴趣的KBar的级别, 必须是分钟级别; 如果不需要KBar, 返回 null 或 empty set
     */
    public Set<PriceLevel> getKBarLevels();

}
