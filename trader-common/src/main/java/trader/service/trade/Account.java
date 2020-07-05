package trader.service.trade;

import java.util.Collection;
import java.util.List;

import trader.common.exception.AppException;
import trader.common.exchangeable.Exchangeable;
import trader.common.util.JsonEnabled;
import trader.service.ServiceConstants.AccountState;

/**
 * 交易账户(真实账户或视图)
 */
public interface Account extends JsonEnabled, TradeConstants {

    /**
     * 唯一ID
     */
    public String getId();

    /**
     * 账户分类: 期货/股票/融资融券
     */
    public AccClassification getClassification();

    /**
     * logger包, 每个账户使用独立日志文件
     */
    public String getLoggerCategory();

    /**
     * 账户状态
     */
    public AccountState getState();

    /**
     */
    public long getMoney(AccMoney mny);

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
    public int getCancelCount(Exchangeable instrument);

    /**
     * 指定品种持仓
     */
    public Position getPosition(Exchangeable instrument);

    /**
     * 返回当日的报单, 按照报单顺序返回
     */
    public List<Order> getOrders();

    /**
     * 根据OrderRef返回报单
     */
    public Order getOrderByRef(String orderRef);

    public Order getOrder(String orderId);

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
    public boolean cancelOrder(String orderId) throws AppException;

    /**
     * 修改一个待成交报单
     */
    public boolean modifyOrder(String orderId, OrderBuilder builder) throws AppException;
}
