package trader.simulator.trade;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import trader.common.exchangeable.Exchangeable;
import trader.common.util.DateUtil;
import trader.common.util.JsonEnabled;
import trader.service.trade.Order;
import trader.service.trade.OrderBuilder;
import trader.service.trade.TradeConstants;

/**
 * 模拟报单. 报单状态改变后, 需要下一个时间片发送回报
 * <BR>目前模拟报单不支持部分成交, 不支持市价
 */
public class SimOrder implements TradeConstants, JsonEnabled {

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

    private Exchangeable instrument;
    private OrderDirection direction;
    private OrderOffsetFlag offsetFlag;
    private SimOrderState state;
    private LocalDateTime[] stateTimes;
    private int volume;
    private long limitPrice;
    private OrderPriceType priceType;
    private long frozenMargin;
    private long frozenCommission;
    private String errorReason;
    private String sysId;
    private String ref;

    public SimOrder(Order order, LocalDateTime time) {
        instrument = order.getInstrument();
        direction = order.getDirection();
        offsetFlag = order.getOffsetFlags();
        volume = order.getVolume(OdrVolume.ReqVolume);
        limitPrice = order.getLimitPrice();
        priceType = order.getPriceType();
        stateTimes = new LocalDateTime[SimOrderState.values().length];
        setState(SimOrderState.Placed, time);
        sysId = nextSysId();
        ref = order.getRef();
        assert(instrument!=null);
    }

    public String getRef() {
        return ref;
    }

    public String getSysId() {
        return sysId;
    }

    public Exchangeable getInstrument() {
        return instrument;
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

    public OrderPriceType getPriceType() {
        return priceType;
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

    public void setState(SimOrderState newState, LocalDateTime stateTime) {
        this.state = newState;
        stateTimes[newState.ordinal()] = stateTime;
    }

    public void setErrorReason(String reason) {
        this.errorReason = reason;
    }

    public void modify(OrderBuilder builder) {
        limitPrice = builder.getLimitPrice();
    }

    private static final AtomicInteger nextSysId = new AtomicInteger();
    private static String nextSysId() {
        int sysId = nextSysId.incrementAndGet();
        return String.format("%06d", sysId);
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("ref", ref);
        json.addProperty("instrument", instrument.id());
        json.addProperty("direction", direction.name());
        json.addProperty("offsetFlag", offsetFlag.name());
        json.addProperty("state", state.name());
        json.addProperty("volume", volume);

        json.addProperty("limitPrice", limitPrice);
        json.addProperty("priceType", priceType.name());

        JsonObject stateTimesJson = new JsonObject();
        for(int i=0;i<stateTimes.length;i++) {
            if ( stateTimes[i]!=null ) {
                stateTimesJson.addProperty(SimOrderState.values()[i].name(), DateUtil.date2str(stateTimes[i]));
            }
        }
        json.add("stateTimes", stateTimesJson);
        return json;
    }

}
