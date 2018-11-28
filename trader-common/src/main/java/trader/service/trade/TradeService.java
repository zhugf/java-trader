package trader.service.trade;

import java.util.Collection;

import trader.common.beans.Lifecycle;

public interface TradeService extends Lifecycle {

    public Account getPrimaryAccount();

    public Account getAccount(String id);

    public AccountView getAccountView(String viewId);

    public Collection<Account> getAccounts();

}
