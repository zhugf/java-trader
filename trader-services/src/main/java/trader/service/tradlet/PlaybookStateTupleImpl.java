package trader.service.tradlet;

import java.time.LocalDate;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import trader.common.util.DateUtil;
import trader.common.util.JsonEnabled;
import trader.service.trade.MarketTimeService;
import trader.service.trade.Order;
import trader.service.trade.TradeConstants.OrderAction;
import trader.service.tradlet.TradletConstants.PlaybookState;

/**
 * Playbook元组信息实现类
 */
public class PlaybookStateTupleImpl implements PlaybookStateTuple, JsonEnabled {

    private PlaybookState state;
    private long timestamp;
    private LocalDate tradingDay;
    private Order order;
    private String orderId;
    private OrderAction orderAction;
    private String actionId;

    PlaybookStateTupleImpl(MarketTimeService mtService, PlaybookState state, Order order, OrderAction orderAction, String tradletActionId){
        this.state = state;
        this.order = order;
        this.orderId = order.getRef();
        this.orderAction = orderAction;
        this.actionId = tradletActionId;
        this.timestamp = mtService.currentTimeMillis();
        tradingDay = mtService.getTradingDay();
    }

    @Override
    public PlaybookState getState() {
        return state;
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }

    public LocalDate getTradingDay() {
        return tradingDay;
    }

    @Override
    public Order getOrder() {
        return order;
    }

    public String getOrderRef() {
        return orderId;
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
        json.addProperty("order", orderId);
        json.addProperty("orderAction", orderAction.name());
        json.addProperty("actionId", actionId);
        json.addProperty("timestamp", timestamp);
        json.addProperty("tradingDay", DateUtil.date2str(tradingDay));
        return json;
    }

    @Override
    public String toString() {
        return "["+state+", orderRef: "+orderId+" action "+orderAction+" id "+actionId+" at "+DateUtil.long2datetime(timestamp)+"]";
    }

}
