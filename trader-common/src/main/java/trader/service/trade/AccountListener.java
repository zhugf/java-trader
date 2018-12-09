package trader.service.trade;

import trader.service.ServiceConstants.AccountState;

public interface AccountListener {
    /**
     * 当账户状态发生变化时被调用
     */
    public void onAccountStateChanged(Account account, AccountState oldState);

    /**
     * 当报单状态发生变化时被调用
     */
    public void onOrderStateChanged(Account account, Order order, OrderStateTuple lastStateTuple);

    /**
     * 当有成交时被调有
     */
    public void onTransaction(Account account, Transaction transaction);
}
