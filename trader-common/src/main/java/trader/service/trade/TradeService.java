package trader.service.trade;

import java.util.List;
import java.util.Map;

import trader.common.beans.ServiceStateAware;

public interface TradeService extends ServiceStateAware, TradeConstants {

    public TradeServiceType getType();

    public Account getPrimaryAccount();

    public Account getAccount(String id);

    public List<Account> getAccounts();

    public Map<String, TxnSessionFactory> getTxnSessionFactories();

    public OrderRefGen getOrderRefGen();

    public void addListener(TradeServiceListener listener);
}
