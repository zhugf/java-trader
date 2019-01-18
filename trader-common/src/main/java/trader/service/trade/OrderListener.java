package trader.service.trade;

/**
 * 报单回调. 每个报单创建时可以有一个专有的回调接口
 */
public interface OrderListener {

    /**
     * 当报单状态发生变化时被调用
     */
    public void onOrderStateChanged(Account account, Order order, OrderStateTuple lastStateTuple);

    /**
     * 当报单有成交时被调有
     */
    public void onTransaction(Account account, Transaction transaction);
}
