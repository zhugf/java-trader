package trader.service.trade;

import java.util.Collection;

import trader.common.exchangeable.Exchangeable;
import trader.common.util.JsonEnabled;

/**
 * 交易费用计算
 */
public interface TxnFeeEvaluator extends JsonEnabled {

    public Collection<Exchangeable> getExchangeables();

}
