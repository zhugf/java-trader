package trader.service.broker;

import java.util.Map;

/**
 * Account信息集中展示
 */
public interface TradeViewMgr {

    public AccountView getAccount(String accId);

    public Map<String, AccountView> getAccounts();

}
