package trader.service.trade;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import trader.common.exchangeable.Exchangeable;
import trader.common.util.JsonEnabled;
import trader.service.trade.TradeConstants.OrderDirection;
import trader.service.trade.TradeConstants.OrderOffsetFlag;
import trader.service.trade.TradeConstants.OrderPriceType;
import trader.service.trade.TradeConstants.OrderVolumeCondition;

/**
 * 报单请求
 */
public class OrderBuilder implements JsonEnabled {
    private AccountView view;
    private Exchangeable exchangeable;
    private OrderDirection direction;
    private OrderPriceType priceType;
    private OrderOffsetFlag offsetFlag;
    private int volume;
    private long limitPrice;
    private OrderVolumeCondition volumeCondition = OrderVolumeCondition.Any;

    public OrderBuilder(AccountView view) {
        this.view = view;
    }

    public AccountView getView() {
        return view;
    }

    public Exchangeable getExchangeable() {
        return exchangeable;
    }

    public OrderDirection getDirection() {
        return direction;
    }

    public OrderPriceType getPriceType() {
        return priceType;
    }

    public OrderOffsetFlag getOffsetFlag() {
        return offsetFlag;
    }

    public int getVolume() {
        return volume;
    }

    public long getLimitPrice() {
        return limitPrice;
    }

    public OrderBuilder setExchagneable(Exchangeable e) {
        this.exchangeable = e;
        return this;
    }

    public OrderBuilder setDirection(OrderDirection direction) {
        this.direction = direction;
        return this;
    }

    public OrderBuilder setPriceType(OrderPriceType priceType) {
        this.priceType = priceType;
        return this;
    }

    public OrderBuilder setOffsetFlag(OrderOffsetFlag offsetFlag) {
        this.offsetFlag = offsetFlag;
        return this;
    }

    public OrderBuilder setVolume(int volume) {
        this.volume = volume;
        return this;
    }

    public OrderBuilder setLimitPrice(long limitPrice) {
        this.limitPrice = limitPrice;
        return this;
    }

    public OrderBuilder setVolumeCondition(OrderVolumeCondition v) {
        this.volumeCondition = v;
        return this;
    }

    public OrderVolumeCondition getVolumeCondition() {
        return volumeCondition;
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        if ( view!=null ) {
            json.addProperty("view", view.getId());
        }
        json.addProperty("exchangeable", getExchangeable().toString());
        json.addProperty("direction", direction.name());
        json.addProperty("offsetFlag", offsetFlag.name());
        json.addProperty("priceType", priceType.name());
        json.addProperty("limitPrice", limitPrice);
        json.addProperty("volume", volume);
        json.addProperty("volumeCondition", volumeCondition.name());
        return json;
    }

    @Override
    public String toString() {
        return toJson().toString();
    }

}
