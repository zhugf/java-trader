package trader.service.trade;

import trader.service.trade.TradeConstants.OrderDirection;
import trader.service.trade.TradeConstants.OrderOffsetFlag;

/**
 * 成交明细
 */
public interface Transaction {

    /**
     * 成交ID
     */
    public String getId();

    public Order getOrder();

    public OrderDirection getDirection();

    public OrderOffsetFlag getOffsetFlag();

    public int getVolume();

    public long getPrice();

    public long getTime();

}
