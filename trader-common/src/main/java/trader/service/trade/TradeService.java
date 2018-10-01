package trader.service.trade;

import java.util.Collection;

public interface TradeService {

    public Account getPrimaryAccount();

    public Account getAccount(String id);

    public Collection<Account> getAccounts();

}
