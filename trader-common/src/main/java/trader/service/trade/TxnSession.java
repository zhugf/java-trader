package trader.service.trade;

import trader.common.util.JsonEnabled;
import trader.service.ServiceConstants.ConnStatus;
import trader.service.trade.TradeConstants.TxnProvider;

public interface TxnSession extends JsonEnabled {

    public TxnProvider getTxnProvider();

    public ConnStatus getStatus();
}
