package trader.service.broker;

import com.google.gson.JsonObject;

public class OrderViewImpl implements OrderView {
    private String id;
    private String ref;
    private JsonObject data;

    public OrderViewImpl(JsonObject orderData) {
    }

    @Override
    public String getId() {
        return id;
    }

    public void update(JsonObject orderData) {
        this.data = orderData;
    }

}
