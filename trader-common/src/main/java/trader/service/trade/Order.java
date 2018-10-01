package trader.service.trade;

import trader.common.exchangeable.Exchangeable;

/**
 * 当日报单
 */
public interface Order extends TradeConstants {

    public Exchangeable exchangeable();

    public String getRef();

    public String getLocalId();

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
