package trader.service.trade;

import java.util.List;

import trader.common.exchangeable.Exchangeable;
import trader.common.util.JsonEnabled;

/**
 * 当日报单.
 * <BR>每个报单有三组唯一序列号:
 * <LI>FrontID+SessionID+OrderRef: 客户端自行维护, 可以随时撤单
 * <LI>ExchangeID+TraderID+OrderLocalID: CTP维护
 * <LI>ExchangeID+OrderSysID: 交易所维护, 可以撤单
 */
public interface Order extends JsonEnabled, TradeConstants {

    public Exchangeable getExchangeable();

    /**
     * 订单状态元组
     */
    public OrderStateTuple getState();

    /**
     * 订单历史状态列表
     */
    public List<OrderStateTuple> getStateTuples();

    public String getRef();

    public String getSysId();

    /**
     * 买卖方向
     */
    public OrderDirection getDirection();

    /**
     * 价格类型
     */
    public OrderPriceType getPriceType();

    /**
     * 开平仓位标志
     */
    public OrderOffsetFlag getOffsetFlags();

    public OrderVolumeCondition getVolumeCondition() ;

    /**
     * 限价
     */
    public long getLimitPrice();

    /**
     * 返回报单资金项
     *
     * @see TradeConstants#OdrMoney_PriceCandidate
     * @see TradeConstants#OdrMoney_LocalUsedMargin
     * @see TradeConstants#OdrMoney_LocalFrozenMargin
     * @see TradeConstants#OdrMoney_LocalUnFrozenMargin
     * @see TradeConstants#OdrMoney_LocalUsedCommission
     * @see TradeConstants#OdrMoney_LocalFrozenCommission
     * @see TradeConstants#OdrMoney_LocalUnfrozenCommission
     */
    public long getMoney(int index);

    /**
     * 返回报单资金项
     */
    public long[] getMoney();

    /**
     * 返回报单仓位项
     *
     * @see TradeConstants#OdrVolume_LongFrozen
     * @see TradeConstants#OdrVolume_ShortFrozen
     * @see TradeConstants#OdrVolume_LongUnfrozen
     * @see TradeConstants#OdrVolume_ShortUnfrozen
     * @see TradeConstants#OdrVolume_TradeVolume
     * @see TradeConstants#OdrVolume_ReqVolume
     */
    public int getVolume(int index);

    public Position getPosition();

    public List<Transaction> getTransactions();

    public String getAttr(String attr);

    public void setAttr(String attr, String value);

}
