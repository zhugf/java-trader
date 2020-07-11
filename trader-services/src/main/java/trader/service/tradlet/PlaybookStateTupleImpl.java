package trader.service.tradlet;

import java.time.LocalDate;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import trader.common.util.DateUtil;
import trader.common.util.JsonEnabled;
import trader.common.util.JsonUtil;
import trader.service.repository.BORepository;
import trader.service.trade.MarketTimeService;
import trader.service.trade.Order;
import trader.service.trade.OrderImpl;
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
    private OrderAction orderAction;
    private String actionId;

    PlaybookStateTupleImpl(MarketTimeService mtService, PlaybookState state, Order order, OrderAction orderAction, String tradletActionId){
        this.state = state;
        this.order = order;
        this.orderAction = orderAction;
        this.actionId = tradletActionId;
        this.timestamp = mtService.currentTimeMillis();
        tradingDay = mtService.getTradingDay();
    }

    PlaybookStateTupleImpl(BORepository repository, JsonObject json){
        this.state = JsonUtil.getPropertyAsEnum(json, "state", PlaybookState.Init, PlaybookState.class);
        this.timestamp = JsonUtil.getPropertyAsLong(json, "timestamp", 0);
        this.tradingDay = JsonUtil.getPropertyAsDate(json, "tradingDay");
        this.orderAction = JsonUtil.getPropertyAsEnum(json, "orderAction", null, OrderAction.class);
        String orderId = JsonUtil.getProperty(json, "orderId", null);
        this.order = OrderImpl.load(repository, orderId, null);
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
    public LocalDate getTradingDay() {
        return tradingDay;
    }

    @Override
    public Order getOrder() {
        return order;
    }

    @Override
    public String getOrderId() {
        if ( order!=null ) {
            return order.getId();
        }
        return null;
    }

    @Override
    public OrderAction getOrderAction() {
        return orderAction;
    }

    @Override
    public String getActionId() {
        return actionId;
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("state", state.name());
        if ( order!=null ) {
            json.addProperty("orderId", order.getId());
        }
        if ( orderAction!=null ) {
            json.addProperty("orderAction", orderAction.name());
        }
        if ( actionId!=null) {
            json.addProperty("actionId", actionId);
        }
        json.addProperty("timestamp", timestamp);
        json.addProperty("tradingDay", DateUtil.date2str(tradingDay));
        return json;
    }

    @Override
    public String toString() {
        return "["+state+", "+(order!=null?"orderRef: "+order.getId():"")+" action "+orderAction+" id "+actionId+" at "+DateUtil.long2datetime(timestamp)+"]";
    }

}
