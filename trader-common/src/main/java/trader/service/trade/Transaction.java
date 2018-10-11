package trader.service.trade;

import java.time.LocalDateTime;

import trader.common.exchangeable.Exchangeable;
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

    public Exchangeable getExchangeable();

    public OrderDirection getDirection();

    public OrderOffsetFlag getOffsetFlag();

    public int getVolume();

    public long getPrice();

    public String getOrderLocalId();

    public String getOrderSysId();

    public LocalDateTime getTime();

}
