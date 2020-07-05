package trader.service.trade;

import java.util.List;

import trader.service.trade.TradeConstants.OrderDirection;
import trader.service.trade.TradeConstants.OrderOffsetFlag;

/**
 * 成交明细
 */
public interface Transaction extends TimedEntity {

    public String getOrderId();

    public OrderDirection getDirection();

    public OrderOffsetFlag getOffsetFlags();

    public int getVolume();

    public long getPrice();

    public long getTime();

    public PositionDetail getOpenDetail();

    public List<PositionDetail> getClosedDetails();
}
