package trader.service.ta;

import org.ta4j.core.TimeSeries;

import trader.common.beans.Lifecycle;
import trader.common.exchangeable.Exchangeable;
import trader.common.tick.PriceLevel;

/**
 * 技术分析/KBar服务
 * <BR>由于KBar的维护耗时极短, 因此放在行情的回调线程中直接完成, 不需要单独线程处理
 */
public interface TAService extends Lifecycle {

    /**
     * 获得某个品种的KBar数据, 如果没有返回null.
     * <BR>要求品种必须是关注行情的品种; 不支持运行到一半时动态增加品种的kBar
     * <BR>对于MIN1以外的KBar, 会动态从MIN1合成
     */
    public TimeSeries getSeries(Exchangeable e, PriceLevel level);

}
