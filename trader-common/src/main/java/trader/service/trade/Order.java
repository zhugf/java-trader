package trader.service.trade;

import trader.common.exchangeable.Exchangeable;

/**
 * 当日报单.
 * <BR>每个报单有三组唯一序列号:
 * <LI>FrontID+SessionID+OrderRef: 客户端自行维护, 可以随时撤单
 * <LI>ExchangeID+TraderID+OrderLocalID: CTP维护
 * <LI>ExchangeID+OrderSysID: 交易所维护, 可以撤单
 */
public interface Order extends TradeConstants {

    public Exchangeable exchangeable();

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

    /**
     * 限价
     */
    public long getLimitPrice();

    /**
     * 数量
     */
    public int getVolume();

    public OrderState getState();

    public OrderSubmitState getSubmitState();

    public long getTime(OrderTime time);

    //public List<Transaction> getTransactions();
}
