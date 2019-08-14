package trader.service.tradlet;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import trader.common.util.DateUtil;
import trader.common.util.JsonEnabled;
import trader.service.trade.Order;
import trader.service.trade.TradeConstants.OrderAction;
import trader.service.tradlet.TradletConstants.PlaybookState;

/**
 * Playbook元组信息实现类
 */
public class PlaybookStateTupleImpl implements PlaybookStateTuple, JsonEnabled {

    private PlaybookState state;
    private long timestamp;
    private Order order;
    private String orderRef;
    private OrderAction orderAction;
    private String actionId;

    PlaybookStateTupleImpl(PlaybookState state, Order order, OrderAction orderAction, String tradletActionId){
        this.state = state;
        this.order = order;
        this.orderRef = order.getRef();
        this.orderAction = orderAction;
        this.actionId = tradletActionId;
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

    public String getOrderRef() {
        return orderRef;
    }

    @Override
    public OrderAction getOrderAction() {
        return orderAction;
    }

    public String getActionId() {
        return actionId;
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("state", state.name());
        json.addProperty("order", orderRef);
        json.addProperty("orderAction", orderAction.name());
        json.addProperty("actionId", actionId);
        json.addProperty("timestamp", timestamp);
        return json;
    }

    @Override
    public String toString() {
        return "["+state+", orderRef: "+orderRef+" action "+orderAction+" id "+actionId+" at "+DateUtil.long2datetime(timestamp)+"]";
    }

}
