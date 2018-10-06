package trader.service.trade;

import trader.common.exchangeable.Exchangeable;

public class OrderImpl implements Order{

    private Exchangeable exchangeable;
    private AccountView view;
    private int volume;
    private long limitPrice;
    private OrderDirection direction;
    private String ref;
    private String sysId;
    private volatile OrderState state;
    private volatile OrderSubmitState submitState;
    private long []times = new long[OrderTime.values().length];

    @Override
    public Exchangeable exchangeable() {
        return exchangeable;
    }

    @Override
    public String getRef() {
        return ref;
    }

    @Override
    public String getSysId() {
        return sysId;
    }

    @Override
    public OrderDirection getDirection() {
        return direction;
    }

    @Override
    public OrderPriceType getPriceType() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public OrderOffsetFlag getOffsetFlags() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public long getLimitPrice() {
        return limitPrice;
    }

    @Override
    public int getVolume() {
        return volume;
    }

    @Override
    public OrderState getState() {
        return state;
    }

    @Override
    public OrderSubmitState getSubmitState() {
        return submitState;
    }

    @Override
    public long getTime(OrderTime time) {
        return times[time.ordinal()];
    }

}
