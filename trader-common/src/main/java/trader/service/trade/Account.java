package trader.service.trade;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import trader.common.util.JsonEnabled;
import trader.service.ServiceConstants.AccountState;

/**
 * 交易账户
 */
public interface Account extends JsonEnabled {

    public String getId();

    public AccountState getState();

    /**
     * @see TradeConstants#AccountMoney_Balance
     * @see TradeConstants#AccountMoney_Available
     * @see TradeConstants#AccountMoney_FrozenMargin
     * @see TradeConstants#AccountMoney_CurrMargin
     * @see TradeConstants#AccountMoney_PreMargin
     * @see TradeConstants#AccountMoney_FrozenCash
     * @see TradeConstants#AccountMoney_Commission
     * @see TradeConstants#AccountMoney_FrozenCommission
     * @see TradeConstants#AccountMoney_CloseProfit
     * @see TradeConstants#AccountMoney_PositionProfit
     * @see TradeConstants#AccountMoney_WithdrawQuota
     * @see TradeConstants#AccountMoney_Reserve
     * @see TradeConstants#AccountMoney_Deposit
     * @see TradeConstants#AccountMoney_Withdraw
     */
    public long getMoney(int moneyIndex);

    public TxnSession getSession();

    public TxnFeeEvaluator getFeeEvaluator();

    /**
     * 基础属性
     */
    public Properties getConnectionProps();

    /**
     * 定义的视图, 视图是与交易策略直接关联的
     */
    public Map<String, AccountView> getViews();

    /**
     * 根据账户视图返回当日持仓, null代表不过滤
     */
    public List<? extends Position> getPositions(AccountView view);

    /**
     * 根据账户视图过滤当日的报单, null代表不过滤
     */
    public List<Order> getOrders(AccountView view);

    public void addAccountListener(AccountListener listener);
}
