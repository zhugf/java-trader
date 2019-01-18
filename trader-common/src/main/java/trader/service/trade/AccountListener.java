package trader.service.trade;

import trader.service.ServiceConstants.AccountState;

public interface AccountListener extends OrderListener {
    /**
     * 当账户状态发生变化时被调用
     */
    public void onAccountStateChanged(Account account, AccountState oldState);

}
