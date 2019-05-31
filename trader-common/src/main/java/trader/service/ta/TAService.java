package trader.service.ta;

import java.util.List;

import trader.common.beans.Lifecycle;
import trader.common.exchangeable.Exchangeable;
import trader.common.tick.PriceLevel;

/**
 * 技术分析/KBar服务
 * <BR>由于KBar的维护耗时极短, 因此放在行情的回调线程中直接完成, 不需要单独线程处理
 */
public interface TAService extends Lifecycle {

    public TAItem getItem(Exchangeable e);

    /**
     * 为指定的品种的特定级别的KBar增加Listener
     * @param exchangeables
     * @param levels
     * @param listener
     */
    public void registerListener(List<Exchangeable> exchangeables, List<PriceLevel> levels, TAListener listener);
}
