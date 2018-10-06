package trader.service.trade;

import trader.common.util.JsonEnabled;
import trader.service.ServiceConstants.ConnState;
import trader.service.trade.TradeConstants.TxnProvider;

public interface TxnSession extends JsonEnabled {

    public TxnProvider getTradeProvider();

    public ConnState getState();
}
