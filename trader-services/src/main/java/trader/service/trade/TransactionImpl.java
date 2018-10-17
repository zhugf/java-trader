package trader.service.trade;

import trader.service.trade.TradeConstants.OrderDirection;
import trader.service.trade.TradeConstants.OrderOffsetFlag;

/**
 * 记载一条成交的明细
 */
public class TransactionImpl implements Transaction {
    private String id;
    private OrderImpl order;
    private OrderDirection direction;
    private OrderOffsetFlag offsetFlag;
    private int volume;
    private long price;
    private long time;

    @Override
    public String getId() {
        return id;
    }

    @Override
    public Order getOrder() {
        return order;
    }

    @Override
    public OrderDirection getDirection() {
        return direction;
    }

    @Override
    public OrderOffsetFlag getOffsetFlag() {
        return offsetFlag;
    }

    @Override
    public int getVolume() {
        return volume;
    }

    @Override
    public long getPrice() {
        return price;
    }

    @Override
    public long getTime() {
        return time;
    }

}
