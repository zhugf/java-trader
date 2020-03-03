package trader.service.trade;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import trader.common.exchangeable.Exchangeable;
import trader.common.util.JsonEnabled;
import trader.common.util.JsonUtil;
import trader.common.util.PriceUtil;

public class OrderImpl implements Order, JsonEnabled {

    protected String id;
    protected Exchangeable instrument;
    protected String ref;
    protected OrderDirection direction;
    protected long limitPrice;
    protected OrderPriceType priceType;
    protected OrderOffsetFlag offsetFlag;
    protected OrderVolumeCondition volumeCondition;
    protected List<OrderStateTuple> stateTuples = new ArrayList<>(32);
    protected OrderStateTuple lastState;
    protected PositionImpl position;
    protected List<Transaction> transactions = new ArrayList<>();
    private Properties attrs;
    protected long money[] = new long[OdrMoney.values().length];
    protected int[] volumes = new int[OdrVolume.values().length];
    protected OrderListener listener;

    public OrderImpl(String id, String ref, OrderBuilder builder, OrderStateTuple stateTuple)
    {
        this.id = id;
        this.ref = ref;
        instrument = builder.getInstrument();
        this.listener = builder.getListener();

        this.direction = builder.getDirection();
        this.priceType = builder.getPriceType();
        this.offsetFlag = builder.getOffsetFlag();
        this.limitPrice = builder.getLimitPrice();
        addVolume(OdrVolume.ReqVolume, builder.getVolume());
        this.volumeCondition = builder.getVolumeCondition();
        this.attrs = builder.getAttrs();

        if ( stateTuple==null ) {
            lastState = OrderStateTuple.STATE_UNKNOWN;
        }else {
            lastState = stateTuple;
            stateTuples.add(stateTuple);
        }
    }

    @Override
    public Exchangeable getInstrument() {
        return instrument;
    }

    @Override
    public OrderListener getListener() {
        return listener;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getRef() {
        return ref;
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
    public OrderStateTuple getStateTuple() {
        return lastState;
    }

    @Override
    public List<OrderStateTuple> getStateTuples(){
        return stateTuples;
    }

    @Override
    public long getMoney(OdrMoney mny) {
        return money[mny.ordinal()];
    }

    @Override
    public long[] getMoney() {
        long[] result = new long[money.length];
        System.arraycopy(money, 0, result, 0, money.length);
        return result;
    }

    @Override
    public int getVolume(OdrVolume vol) {
        return volumes[vol.ordinal()];
    }

    public int addVolume(OdrVolume vol, int volume) {
        volumes[vol.ordinal()] += volume;
        return volumes[vol.ordinal()];
    }

    public long setMoney(OdrMoney mny, long newValue) {
        long result = money[mny.ordinal()];
        money[mny.ordinal()] = newValue;
        return result;
    }

    public long addMoney(OdrMoney mny, long toadd) {
        long result = money[mny.ordinal()];
        money[mny.ordinal()] += toadd;
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

    public void setLimitPrice(long limitPrice) {
        this.limitPrice = limitPrice;
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("instrument", instrument.id());
        json.addProperty("id", id);
        json.addProperty("ref", ref);
        json.addProperty("direction", direction.name());
        json.addProperty("limitPrice", limitPrice);
        json.addProperty("priceType", priceType.name());
        json.addProperty("offsetFlag", offsetFlag.name());
        json.addProperty("volumeCondition", volumeCondition.name());
        json.add("lastState", lastState.toJson());
        json.add("stateTuples", JsonUtil.object2json(stateTuples));
        if ( !attrs.isEmpty() ) {
            json.add("attrs", JsonUtil.object2json(attrs));
        }
        json.add("money", TradeConstants.odrMoney2json(money));
        json.add("volumes", TradeConstants.odrVolume2json(volumes));
        if( !transactions.isEmpty()) {
            JsonArray txnIds = new JsonArray();
            for(Transaction txn:transactions) {
                txnIds.add(txn.getId());
            }
            json.add("txnIds", txnIds);
        }
        return json;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder(128);
        result
            .append(getRef()).append(" ")
            .append(getOffsetFlags()).append(" ")
            .append(getDirection()).append(" ")
            .append(PriceUtil.long2str(getLimitPrice())).append(" ")
            .append(getVolume(OdrVolume.ReqVolume));
        return result.toString();
    }

    @Override
    public boolean equals(Object o) {
        if ( o==this ) {
            return true;
        }
        if ( o==null || !(o instanceof OrderImpl)) {
            return false;
        }
        OrderImpl order = (OrderImpl)o;
        return ref.equals(order.ref);
    }

    @Override
    public int hashCode() {
        return ref.hashCode();
    }

    /**
     * 当非结束状态时, 可更新状态
     * @return 旧状态, null 如果切换状态不成功
     */
    OrderStateTuple changeState(OrderStateTuple newState) {
        OrderStateTuple result = null;
        if ( !lastState.getState().isDone() && !lastState.equals(newState) ) {
            result = lastState;
            lastState = newState;
            stateTuples.add(newState);
        }
        return result;
    }

    void attachPosition(PositionImpl position) {
        this.position = position;
    }

    /**
     * 是否接受成交事件
     * @return
     */
    boolean attachTransaction(Transaction txn, long[] txnFees, long timestamp) {
        boolean txnAccepted = true;
        int txnVolume = txn.getVolume();
        if ( (getVolume(OdrVolume.ReqVolume)-getVolume(OdrVolume.TradeVolume))<txnVolume ) {
            txnAccepted = false;
        }

        if ( !txnAccepted ) {
            return false;
        }
        transactions.add(txn);
        int tradeVolume = addVolume(OdrVolume.TradeVolume, txnVolume);
        boolean stateChangedToComplete = false;
        if ( getVolume(OdrVolume.ReqVolume) == tradeVolume ) {
            //全部成交, 切换状态到Complete
            changeState(new OrderStateTuple(OrderState.Complete, OrderSubmitState.Accepted, timestamp, null));
            stateChangedToComplete = true;
        }else {
            //部分成交, 切换状态到ParticallyComplete
            changeState(new OrderStateTuple(OrderState.ParticallyComplete, OrderSubmitState.Accepted, timestamp, null));
        }

        switch ( getOffsetFlags() ){
        case CLOSE:
        case CLOSE_TODAY:
        case CLOSE_YESTERDAY:
            //如果是平仓, 需要扣除冻结仓位
            if ( txn.getDirection()==OrderDirection.Sell ) {
                //平多
                addVolume(OdrVolume.LongUnfrozen, txnVolume);
            } else {
                //平空
                addVolume(OdrVolume.ShortUnfrozen, txnVolume);
            }
            //平仓只在Position中调整保证金
            break;
        case OPEN:
            //开仓--需要计算资金变动

            //计算平均开仓价
            long totalCost = 0;
            for(int i=0;i<transactions.size();i++) {
                Transaction txn0 = transactions.get(i);
                totalCost += txn0.getPrice()*txn0.getVolume();
            }
            setMoney(OdrMoney.OpenCost, totalCost/tradeVolume);

            //调整保证金
            addMoney(OdrMoney.LocalUsedMargin, txnFees[0]);
            if ( addMoney(OdrMoney.LocalUnfrozenMargin, txnFees[0]) >  getMoney(OdrMoney.LocalFrozenMargin)) {
                setMoney(OdrMoney.LocalUnfrozenMargin, getMoney(OdrMoney.LocalFrozenMargin));
            }

        }
        //调整手续费
        addMoney(OdrMoney.LocalUsedCommission, txnFees[1]);
        addMoney(OdrMoney.LocalUnfrozenCommission, txnFees[1]);
        if ( stateChangedToComplete ) {
            //报单完成, 更新资金变动
            setMoney(OdrMoney.LocalUnfrozenMargin, getMoney(OdrMoney.LocalFrozenMargin));
            setMoney(OdrMoney.LocalUnfrozenCommission, getMoney(OdrMoney.LocalFrozenCommission));
        }
        return true;
    }

}
