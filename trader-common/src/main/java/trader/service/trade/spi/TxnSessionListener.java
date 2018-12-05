package trader.service.trade.spi;

import trader.service.ServiceConstants.ConnState;
import trader.service.trade.Order;
import trader.service.trade.OrderStateTuple;
import trader.service.trade.Transaction;
import trader.service.trade.TxnSession;

public interface TxnSessionListener {

    public void onTxnSessionStateChanged(TxnSession session, ConnState lastState);

    public void onTransaction(Order order, Transaction txn, long txnTimestamp);

    public OrderStateTuple changeOrderState(Order order, OrderStateTuple oldState);

    public void compareAndSetRef(String orderRef);
}
