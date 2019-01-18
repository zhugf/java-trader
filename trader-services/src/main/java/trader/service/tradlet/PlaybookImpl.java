package trader.service.tradlet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import trader.common.util.JsonEnabled;
import trader.common.util.JsonUtil;
import trader.service.trade.Order;
import trader.service.trade.TradeConstants.PosDirection;

/**
 * 交易剧本实现类
 */
public class PlaybookImpl implements Playbook, JsonEnabled {
    private String id;
    private String templateId;
    private int volume;
    private long price;
    private PosDirection direction = PosDirection.Net;
    private Map<String, Object> attrs = new HashMap<>();
    private List<Order> orders = new ArrayList<>();
    private Order pendingOrder;
    private List<PlaybookStateTuple> stateTuples = new ArrayList<>();
    private PlaybookStateTuple stateTuple;

    public PlaybookImpl(String id, PlaybookBuilder builder, PlaybookStateTuple openState) {
        this.id = id;
        this.stateTuple = openState;
        stateTuples.add(openState);

        direction = builder.getOpenDirection();
        volume = builder.getVolume();
        price = builder.getOpenPrice();
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getTemplateId() {
        return templateId;
    }

    @Override
    public List<PlaybookStateTuple> getStateTuples() {
        return stateTuples ;
    }

    @Override
    public PlaybookStateTuple getStateTuple() {
        return stateTuple;
    }

    @Override
    public Object getAttr(String attr) {
        return attrs.get(attr);
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
    public PosDirection getDirection() {
        return direction;
    }

    @Override
    public List<Order> getOrders() {
        return orders;
    }

    @Override
    public Order getPendingOrder() {
        return pendingOrder;
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("stateTuple", stateTuple.toString());
        json.addProperty("volume", volume);
        json.addProperty("direction", direction.name());
        if( attrs!=null ) {
            json.add("attrs", JsonUtil.object2json(attrs));
        }
        return json;
    }

}
