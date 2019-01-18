package trader.service.tradlet;

import trader.service.trade.Order;
import trader.service.tradlet.TradletConstants.PlaybookState;

/**
 * Playbook元组信息实现类
 */
public class PlaybookStateTupleImpl implements PlaybookStateTuple {

    private PlaybookState state;
    private long timestamp;
    private Order order;

    PlaybookStateTupleImpl(PlaybookState state, Order order){
        this.state = state;
        this.order = order;
        this.timestamp = System.currentTimeMillis();
    }

    @Override
    public PlaybookState getState() {
        return state;
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public Order getOrder() {
        return order;
    }

}
