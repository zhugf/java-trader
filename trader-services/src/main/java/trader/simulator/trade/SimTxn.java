package trader.simulator.trade;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import trader.service.trade.TradeConstants.OrderDirection;

/**
 * 模拟成交
 */
public class SimTxn {
    private SimOrder order;
    private String id;
    private OrderDirection direction;
    private int volume;
    private long price;
    private LocalDateTime time;

    public SimTxn(SimOrder order, long price, LocalDateTime time) {
        this.order = order;
        id = nextTxnId();
        this.direction = order.getDirection();
        this.volume = order.getVolume();
        this.price = price;
        this.time = time;
    }

    public String getId() {
        return id;
    }

    public SimOrder getOrder() {
        return order;
    }

    public int getVolume() {
        return volume;
    }

    public long getPrice() {
        return price;
    }

    public OrderDirection getDirection() {
        return direction;
    }

    public LocalDateTime getTime() {
        return time;
    }

    private static final AtomicInteger nextTxnId = new AtomicInteger();
    private static String nextTxnId() {
        int txnId = nextTxnId.incrementAndGet();
        return String.format("%06d", txnId);
    }

}
