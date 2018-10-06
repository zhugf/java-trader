package trader.service.trade;

import trader.service.ServiceConstants.AccountState;

public interface AccountListener {

    public void onAccountStateChanged(Account account, AccountState oldState);

}
