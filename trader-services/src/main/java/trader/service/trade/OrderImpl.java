package trader.service.trade;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import com.google.gson.JsonObject;

import trader.common.exchangeable.Exchangeable;
import trader.common.util.JsonUtil;

public class OrderImpl implements Order {

    protected Exchangeable exchangeable;
    protected String ref;
    protected OrderDirection direction;
    protected long limitPrice;
    protected OrderPriceType priceType;
    protected OrderOffsetFlag offsetFlag;
    protected OrderVolumeCondition volumeCondition;
    protected String sysId;
    protected volatile OrderState state;
    protected volatile OrderSubmitState submitState;
    protected long[] stateTimes = new long[OrderState.values().length];
    protected String failReason;
    protected PositionImpl position;
    protected List<Transaction> transactions = new ArrayList<>();
    private Properties attrs = new Properties();
    protected long money[] = new long[OdrMoney_Count];
    protected int[] volumes = new int[OdrVolume_Count];

    public OrderImpl(Exchangeable e, String ref, OrderPriceType priceType, OrderOffsetFlag offsetFlag, long limitPrice, int volume, OrderVolumeCondition volumeCondition)
    {
        exchangeable = e;
        this.ref = ref;
        this.priceType = priceType;
        this.offsetFlag = offsetFlag;
        this.limitPrice = limitPrice;
        setVolume(OdrVolume_ReqVolume, volume);
        this.volumeCondition = volumeCondition;
        state = OrderState.Unknown;
        submitState = OrderSubmitState.Unsubmitted;
    }

    @Override
    public Exchangeable getExchangeable() {
        return exchangeable;
    }

    @Override
    public String getRef() {
        return ref;
    }

    @Override
    public String getSysId() {
        return sysId;
    }

    @Override
    public OrderDirection getDirection() {
        return direction;
    }

    @Override
    public OrderPriceType getPriceType() {
        return priceType;
    }

    @Override
    public OrderOffsetFlag getOffsetFlags() {
        return offsetFlag;
    }

    @Override
    public OrderVolumeCondition getVolumeCondition() {
        return volumeCondition;
    }

    @Override
    public long getLimitPrice() {
        return limitPrice;
    }

    @Override
    public OrderState getState() {
        return state;
    }

    @Override
    public OrderSubmitState getSubmitState() {
        return submitState;
    }

    @Override
    public long getMoney(int index) {
        return money[index];
    }

    @Override
    public int getVolume(int index) {
        return volumes[index];
    }

    private void setVolume(int index, int volume) {
        volumes[index] = volume;
    }

    public long setMoney(int index, long newValue) {
        long result = money[index];
        money[index] = newValue;
        return result;
    }

    public long addMoney(int index, long toadd) {
        long result = money[index];
        money[index] += toadd;
        return result;
    }

    @Override
    public String getAttr(String attr) {
        return attrs.getProperty(attr);
    }

    @Override
    public void setAttr(String attr, String value) {
        if ( value==null ) {
            attrs.remove(attr);
        }else {
            attrs.setProperty(attr, value);
        }
    }

    @Override
    public List<Transaction> getTransactions() {
        return transactions;
    }

    @Override
    public Position getPosition() {
        return position;
    }

    @Override
    public JsonObject toJsonObject() {
        JsonObject json = new JsonObject();
        json.addProperty("exchangeable", exchangeable.id());
        json.addProperty("ref", ref);
        json.addProperty("direction", direction.name());
        json.addProperty("limitPrice", limitPrice);
        json.addProperty("priceType", priceType.name());
        json.addProperty("offsetFlag", offsetFlag.name());
        json.addProperty("state", state.name());
        json.addProperty("submitState", submitState.name());
        if ( failReason!=null ) {
            json.addProperty("failReason", failReason);
        }
        json.add("stateTimes", JsonUtil.object2json(stateTimes));
        if ( !attrs.isEmpty() ) {
            json.add("attrs", JsonUtil.object2json(attrs));
        }
        json.add("money", JsonUtil.pricelong2array(money));
        json.add("volumes", JsonUtil.object2json(volumes));
        return json;
    }

    @Override
    public String toString() {
        return toJsonObject().toString();
    }

    public OrderState setState(OrderState state) {
        OrderState lastState = this.state;
        this.state = state;
        stateTimes[state.ordinal()] = System.currentTimeMillis();
        return lastState;
    }

    public void setSubmitState(OrderSubmitState submitState) {
        this.submitState = submitState;
    }

    public void setSysId(String sysId) {
        this.sysId = sysId;
    }

    public void setFailReason(String failReason) {
        this.failReason = failReason;
    }

    public void attachTransaction(Transaction txn) {
        transactions.add(txn);
    }

    public void attachPosition(PositionImpl position) {
        this.position = position;
    }

}
