package trader.service.trade;

import java.util.Collection;

import trader.common.exchangeable.Exchangeable;
import trader.common.util.JsonEnabled;
import trader.service.trade.TradeConstants.OrderDirection;
import trader.service.trade.TradeConstants.OrderOffsetFlag;

/**
 * 交易费用计算
 */
public interface TxnFeeEvaluator extends JsonEnabled {

    public Collection<Exchangeable> getExchangeables();

    /**
     * 返回最小价格变动单位
     */
    public long getPriceTick(Exchangeable e);

    /**
     * 计算保证金和手续费
     */
    public long[] compute(Exchangeable e, int volume, long price, OrderDirection direction, OrderOffsetFlag offsetFlag);

}
