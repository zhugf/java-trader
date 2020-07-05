package trader.service.trade;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import trader.common.util.JsonEnabled;
import trader.common.util.JsonUtil;
import trader.service.trade.TradeConstants.OrderState;
import trader.service.trade.TradeConstants.OrderSubmitState;

/**
 * 订单状态变化元组
 */
public class OrderStateTuple implements JsonEnabled, Comparable<OrderStateTuple> {
    public static final OrderStateTuple STATE_UNKNOWN = new OrderStateTuple(OrderState.Unknown, OrderSubmitState.Unsubmitted, 0);

    private OrderState state;
    private OrderSubmitState submitState;
    private long timestamp;
    private String stateMessage;

    public OrderStateTuple(OrderState state, OrderSubmitState submitState, long timestamp){
        this(state, submitState, timestamp, null);
    }

    public OrderStateTuple(OrderState state, OrderSubmitState submitState, long timestamp, String stateMessage) {
        this.state = state;
        this.submitState = submitState;
        this.timestamp = timestamp;
        this.stateMessage = stateMessage;
    }

    public OrderStateTuple(JsonObject json) {
        state = JsonUtil.getPropertyAsEnum(json, "state", null, OrderState.class);
        submitState = JsonUtil.getPropertyAsEnum(json, "submitState", null, OrderSubmitState.class);
        timestamp = JsonUtil.getPropertyAsLong(json, "timestamp", 0);
        stateMessage = JsonUtil.getProperty(json, "stateMessage", null);
    }

    /**
     * 订单状态
     */
    public OrderState getState() {
        return state;
    }

    /**
     * 订单命令提交状态
     */
    public OrderSubmitState getSubmitState() {
        return submitState;
    }

    /**
     * 订单状态更新时间戳
     */
    public long getTimestamp() {
        return timestamp;
    }

    public String getStateMessage() {
        return stateMessage;
    }

    @Override
    public int hashCode() {
        return state.hashCode()*1000+submitState.hashCode()+ (int)(timestamp>>16);
    }

    @Override
    public boolean equals(Object o) {
        if ( this==o ) {
            return true;
        }
        if ( o==null || !(o instanceof OrderStateTuple) ) {
            return false;
        }
        OrderStateTuple tuple = (OrderStateTuple)o;
        return tuple.timestamp == timestamp && tuple.state==state && tuple.submitState == submitState;
    }

    @Override
    public int compareTo(OrderStateTuple o) {
        return (int)(timestamp-o.timestamp);
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("state", state.name());
        json.addProperty("submitState", submitState.name());
        json.addProperty("timestamp", timestamp);
        if ( stateMessage!=null ) {
            json.addProperty("stateMessage", stateMessage);
        }
        return json;
    }

    @Override
    public String toString() {
        return toJson().toString();
    }
}
