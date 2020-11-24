package trader.service.broker;

import java.util.List;

import trader.common.beans.Identifiable;
import trader.common.util.JsonEnabled;
import trader.service.ServiceConstants.AccountState;

/**
 * 账户信息(只读)
 */
public interface AccountView extends Identifiable, JsonEnabled {

    public AccountState getState();

    public List<OrderView> getOrders();

    public List<TransactionView> getTransactions();

}
