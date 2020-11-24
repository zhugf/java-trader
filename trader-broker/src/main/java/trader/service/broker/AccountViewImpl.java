package trader.service.broker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import trader.common.util.ConversionUtil;
import trader.common.util.JsonUtil;
import trader.service.ServiceConstants.AccountState;
import trader.service.trade.TradeConstants;

public class AccountViewImpl implements AccountView{

    private String id;

    private AccountState state;

    private long[] money;

    private Map data = new HashMap();

    private LinkedHashMap<String, OrderViewImpl> orders = new LinkedHashMap<>();

    public AccountViewImpl(Map data) {
        id = ConversionUtil.toString(data.get("id"));
        this.data = data;
        update(data);
    }

    public String getId() {
        return id;
    }

    public AccountState getState() {
        return state;
    }

    public long[] getMoney() {
        return money;
    }

    @Override
    public List<OrderView> getOrders() {
        return new ArrayList(orders.values());
    }

    @Override
    public List<TransactionView> getTransactions() {
        return null;
    }

    public synchronized void update(Map data) {
        if ( this.data!=data ) {
            this.data.putAll(data);
        }
        state = ConversionUtil.toEnum(AccountState.class, data.get("state"));
        money = TradeConstants.json2accMoney((JsonObject)JsonUtil.object2json(data.get("money")));
    }

    public synchronized void updateOrder(JsonObject orderData) {
        String orderId = orderData.get("id").getAsString();
        OrderViewImpl orderView = orders.get(orderId);
        if ( null==orderView ) {
            orderView = new OrderViewImpl(orderData);
            orders.put(orderId, orderView);
        } else {
            orderView.update(orderData);
        }
    }

    @Override
    public JsonElement toJson() {
        return JsonUtil.object2json(data);
    }

}
