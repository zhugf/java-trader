package trader.service.trade;

import java.util.Collection;

import trader.common.exchangeable.Exchangeable;
import trader.common.util.JsonEnabled;
import trader.service.trade.TradeConstants.OrderDirection;
import trader.service.trade.TradeConstants.OrderOffsetFlag;
import trader.service.trade.TradeConstants.PosDirection;

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
     * 计算保证金和手续费.
     *
     * @return 0 保证金, 1 手续费, 2 合约价值
     */
    public long[] compute(Exchangeable e, int volume, long price, OrderDirection direction, OrderOffsetFlag offsetFlag);

    public long[] compute(Transaction txn);

    /**
     * 计算保证金和和合约价值
     *
     * @return 0 保证金 1 合约价值
     */
    public long[] compute(Exchangeable e, int volume, long price, PosDirection direction);
}
