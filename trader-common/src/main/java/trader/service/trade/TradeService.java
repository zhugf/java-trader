package trader.service.trade;

import java.util.Collection;
import java.util.Map;

import trader.common.beans.Lifecycle;
import trader.common.util.TimestampSeqGen;

public interface TradeService extends Lifecycle {

    public Account getPrimaryAccount();

    public Account getAccount(String id);

    public Collection<Account> getAccounts();

    public Map<String, TxnSessionFactory> getTxnSessionFactories();

    public TimestampSeqGen getOrderIdGen();

    public OrderRefGen getOrderRefGen();

}
