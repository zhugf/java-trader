package trader.simulator.trade;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import trader.common.exchangeable.Exchangeable;
import trader.service.trade.Order;
import trader.service.trade.TradeConstants;

/**
 * 模拟报单. 报单状态改变后, 需要下一个时间片发送回报
 * <BR>目前模拟报单不支持部分成交, 不支持市价
 */
public class SimOrder implements TradeConstants {

    public static enum SimOrderState {
        /**
         * 报单异常
         */
        Invalid
        /**
         * 报单成功--等待成交
         */
        ,Placed
        /**
         * 已成交
         */
        ,Completed
        /**
         * 已取消
         */
        ,Canceled
    };

    private LocalDateTime submitTime;
    private Exchangeable e;
    private OrderDirection direction;
    private OrderOffsetFlag offsetFlag;
    private SimOrderState state;
    private int volume;
    private long limitPrice;
    private long frozenMargin;
    private long frozenCommission;
    private String errorReason;
    private String sysId;
    private String ref;

    public SimOrder(Order order, LocalDateTime time) {
        e = order.getExchangeable();
        direction = order.getDirection();
        this.offsetFlag = order.getOffsetFlags();
        volume = order.getVolume(OdrVolume_ReqVolume);
        limitPrice = order.getLimitPrice();
        submitTime = time;
        sysId = nextSysId();
        ref = order.getRef();
    }

    public String getRef() {
        return ref;
    }

    public String getSysId() {
        return sysId;
    }

    public Exchangeable getExchangeable() {
        return e;
    }

    public SimOrderState getState() {
        return state;
    }

    public OrderDirection getDirection() {
        return direction;
    }

    public OrderOffsetFlag getOffsetFlag() {
        return offsetFlag;
    }

    public long getLimitPrice() {
        return limitPrice;
    }

    public int getVolume() {
        return volume;
    }

    public long getFrozenMargin() {
        return frozenMargin;
    }

    public void setFrozenMargin(long frozenMargin) {
        this.frozenMargin = frozenMargin;
    }

    public long getFrozenCommission() {
        return frozenCommission;
    }

    public void setFrozenCommission(long frozenCommission) {
        this.frozenCommission = frozenCommission;
    }

    public String getErrorReason() {
        return errorReason;
    }

    public void setState(SimOrderState newState) {
        this.state = newState;
    }

    public void setErrorReason(String reason) {
        this.errorReason = reason;
    }

    private static final AtomicInteger nextSysId = new AtomicInteger();
    private static String nextSysId() {
        int sysId = nextSysId.incrementAndGet();
        return String.format("%06d", sysId);
    }
}
