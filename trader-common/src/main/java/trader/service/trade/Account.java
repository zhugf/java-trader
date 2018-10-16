package trader.service.trade;

import java.util.Collection;
import java.util.Map;
import java.util.Properties;

import trader.common.exception.AppException;
import trader.common.exchangeable.Exchangeable;
import trader.common.util.JsonEnabled;
import trader.service.ServiceConstants.AccountState;

/**
 * 交易账户
 */
public interface Account extends JsonEnabled {

    public String getId();

    public AccountState getState();

    /**
     * @see TradeConstants#AccMoney_Balance
     * @see TradeConstants#AccMoney_Available
     * @see TradeConstants#AccMoney_FrozenMargin
     * @see TradeConstants#AccMoney_CurrMargin
     * @see TradeConstants#AccMoney_PreMargin
     * @see TradeConstants#AccMoney_FrozenCash
     * @see TradeConstants#AccMoney_Commission
     * @see TradeConstants#AccMoney_FrozenCommission
     * @see TradeConstants#AccMoney_CloseProfit
     * @see TradeConstants#AccMoney_PositionProfit
     * @see TradeConstants#AccMoney_WithdrawQuota
     * @see TradeConstants#AccMoney_Reserve
     * @see TradeConstants#AccMoney_Deposit
     * @see TradeConstants#AccMoney_Withdraw
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
    public Map<String, ? extends AccountView> getViews();

    /**
     * 根据账户视图返回当日持仓, null代表不过滤
     */
    public Collection<? extends Position> getPositions(AccountView view);

    public Position getPosition(Exchangeable e);

    /**
     * 根据账户视图过滤当日的报单, null代表不过滤
     */
    public Collection<? extends Order> getOrders();

    /**
     * 根据OrderRef返回报单
     */
    public Order getOrder(String orderRef);

    public void addAccountListener(AccountListener listener);

    /**
     * 提交一个报单
     * @throws AppException 本地检查失败, 或报单归属的账户视图限额已满
     */
    public Order createOrder(OrderBuilder builder) throws AppException;

}
