package trader.service.trade;

import java.util.Collection;
import java.util.List;

import trader.common.exception.AppException;
import trader.common.exchangeable.Exchangeable;
import trader.common.util.JsonEnabled;
import trader.service.ServiceConstants.AccountState;
import trader.service.data.KVStore;

/**
 * 交易账户(真实账户或视图)
 */
public interface Account extends JsonEnabled {

    /**
     * 唯一ID
     */
    public String getId();

    /**
     * logger包, 每个账户使用独立日志文件
     */
    public String getLoggerCategory();

    /**
     * 账户状态
     */
    public AccountState getState();

    /**
     * Account独有的KVStore
     */
    public KVStore getStore();

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

    /**
     * 账户相关的手续费/保证金率计算接口
     */
    public TxnFeeEvaluator getFeeEvaluator();

    /**
     * 账户交易API接口
     */
    public TxnSession getSession();

    /**
     * 所有持仓, 无序
     */
    public Collection<? extends Position> getPositions();

    /**
     * 返回合约的当日撤单数
     */
    public int getCancelCount(Exchangeable e);

    /**
     * 指定品种持仓
     */
    public Position getPosition(Exchangeable e);

    /**
     * 返回当日的报单, 按照报单顺序返回
     */
    public List<? extends Order> getOrders();

    /**
     * 根据OrderRef返回报单
     */
    public Order getOrder(String orderRef);

    /**
     * 增加侦听只有当新增Tradlet Group时, 才会调用这个函数.
     */
    public void addAccountListener(AccountListener listener);

    /**
     * 删除指定listener
     */
    public void removeAccountListener(AccountListener listener);

    /**
     * 创建并提交一个报单
     * @throws AppException 本地检查失败, 或报单归属的账户视图限额已满
     */
    public Order createOrder(OrderBuilder builder) throws AppException;

    /**
     * 取消一个待成交报单
     */
    public boolean cancelOrder(String orderRef) throws AppException;

    /**
     * 修改一个待成交报单
     */
    public boolean modifyOrder(String orderRef, OrderBuilder builder) throws AppException;
}
