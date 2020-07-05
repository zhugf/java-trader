package trader.service.trade.spi;

import java.util.Map;

import com.google.gson.JsonObject;

import trader.common.exchangeable.Exchangeable;
import trader.service.ServiceConstants.ConnState;
import trader.service.trade.Order;
import trader.service.trade.OrderStateTuple;
import trader.service.trade.TradeConstants.AccountTransferAction;
import trader.service.trade.TradeConstants.OrderDirection;
import trader.service.trade.TradeConstants.OrderOffsetFlag;
import trader.service.trade.TxnSession;

public interface TxnSessionListener {

    /**
     * 当交易链接状态改变时回调
     */
    public void onTxnSessionStateChanged(TxnSession session, ConnState lastState);

    /**
     * 当有成交回报时回调
     */
    public void onTransaction(
            String txnId,
            Exchangeable instrument,
            String orderRef,
            OrderDirection txnDirection,
            OrderOffsetFlag txnFlag,
            long txnPrice,
            int txnVolume,
            long time,
            Object txnData
        );

    /**
     * 当订单状态发生变化时回调
     *
     * @param orderRef
     */
    public OrderStateTuple onOrderStateChanged(Order order, OrderStateTuple newState, Map<String, String> attrs);

    /**
     * 检查报单是否存在
     */
    public Order getOrderByRef(String orderRef);

    /**
     * 根据回报创建一个报单
     */
    public Order createOrderFromResponse(JsonObject orderInfo);

    /**
     * 账户入金出金时调用
     */
    public void onAccountTransfer(AccountTransferAction action, long tradeAmount);
}
