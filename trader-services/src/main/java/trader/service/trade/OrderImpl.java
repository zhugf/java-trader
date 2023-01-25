package trader.service.trade;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.eclipse.jetty.util.StringUtil;

import com.alibaba.druid.sql.ast.expr.SQLCharExpr;
import com.alibaba.druid.sql.ast.expr.SQLListExpr;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import trader.common.exchangeable.Future;
import trader.common.util.DateUtil;
import trader.common.util.JsonEnabled;
import trader.common.util.JsonUtil;
import trader.common.util.PriceUtil;
import trader.service.repository.BOEntityIterator;
import trader.service.repository.BORepository;
import trader.service.repository.BORepositoryConstants.BOEntityType;
import trader.service.tradlet.PlaybookImpl;
import trader.service.tradlet.TradletConstants.PlaybookState;

public class OrderImpl extends AbsTimedEntity implements Order, JsonEnabled {

    protected String ref;
    protected OrderDirection direction;
    protected long limitPrice;
    protected OrderPriceType priceType;
    protected OrderOffsetFlag offsetFlag;
    protected OrderVolumeCondition volumeCondition;
    protected List<OrderStateTuple> stateTuples = new ArrayList<>(32);
    protected OrderStateTuple lastState;
    protected List<Transaction> transactions = new ArrayList<>();
    protected List<String> transactionIds = new ArrayList<>();
    private Properties attrs;
    protected long money[] = new long[OdrMoney.values().length];
    protected int[] volumes = new int[OdrVolume.values().length];
    protected OrderListener listener;

    public OrderImpl(String id, AccountImpl account, LocalDate tradingDay, String ref, OrderBuilder builder, OrderStateTuple stateTuple, long currTime)
    {
        super(id, account.getId(), builder.getInstrument(), tradingDay, currTime);
        this.ref = ref;
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

    OrderImpl(BORepository repository, JsonObject json){
        super(JsonUtil.getProperty(json, "id", null),
                JsonUtil.getProperty(json,"accountId",null),
                Future.fromString(json.get("instrument").getAsString()),
                JsonUtil.getPropertyAsDate(json, "tradingDay"),
                JsonUtil.getPropertyAsLong(json, "createTime", 0)
            );
        this.ref = JsonUtil.getProperty(json,"ref",null);
        this.direction = JsonUtil.getPropertyAsEnum(json, "direction", OrderDirection.Buy, OrderDirection.class);
        this.offsetFlag = JsonUtil.getPropertyAsEnum(json, "offsetFlag", OrderOffsetFlag.OPEN, OrderOffsetFlag.class);
        this.limitPrice = JsonUtil.getPropertyAsPrice(json, "limitPrice", 0);
        this.priceType = JsonUtil.getPropertyAsEnum(json, "priceType", OrderPriceType.LimitPrice, OrderPriceType.class);
        this.volumeCondition = JsonUtil.getPropertyAsEnum(json, "volumeCondition", OrderVolumeCondition.Any, OrderVolumeCondition.class);
        money = TradeConstants.json2OdrMoney(json.get("money").getAsJsonObject());
        volumes = TradeConstants.json2OdrVolume(json.get("volumes").getAsJsonObject());
        JsonArray stateTuples = json.get("stateTuples").getAsJsonArray();
        for(int i =0;i<stateTuples.size();i++) {
            this.stateTuples.add(new OrderStateTuple(stateTuples.get(i).getAsJsonObject()));
        }
        lastState = this.stateTuples.get(this.stateTuples.size()-1);
        this.attrs = new Properties();
        JsonObject attrs = json.get("attrs").getAsJsonObject();
        for(String key:attrs.keySet()) {
            this.attrs.setProperty(key, attrs.get(key).getAsString());
        }
        this.transactionIds =  (List)JsonUtil.json2value(json.get("transactionIds"));
        for(String txnId:transactionIds) {
            this.transactions.add(TransactionImpl.load(repository, txnId, null));
        }
    }

    @Override
    public OrderListener getListener() {
        return listener;
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

    public List<String> getTransactionIds(){
        return transactionIds;
    }

    public void setLimitPrice(long limitPrice) {
        this.limitPrice = limitPrice;
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = super.toJson().getAsJsonObject();
        json.addProperty("ref", ref);
        json.addProperty("direction", direction.name());
        json.addProperty("limitPrice", PriceUtil.long2str(limitPrice) );
        json.addProperty("priceType", priceType.name());
        json.addProperty("offsetFlag", offsetFlag.name());
        json.addProperty("volumeCondition", volumeCondition.name());
        json.addProperty("state", lastState.getState().name());
        json.add("stateTuples", JsonUtil.object2json(stateTuples));
        json.add("attrs", JsonUtil.object2json(attrs));
        json.add("money", TradeConstants.odrMoney2json(money));
        json.add("volumes", TradeConstants.odrVolume2json(volumes));
        json.add("transactionIds", JsonUtil.object2json(transactionIds));
        return json;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder(128);
        result
            .append(getId()).append(" ")
            .append(getRef()).append(" ")
            .append(getOffsetFlags()).append(" ")
            .append(getDirection()).append(" P")
            .append(PriceUtil.long2str(getLimitPrice())).append(" V")
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
            updateTime = newState.getTimestamp();
        }
        return result;
    }

    /**
     * 接受成交, 并更新保证金/手续费占用,平均开仓成本等字段, 更新成交量字段
     */
    boolean attachTransaction(Transaction txn, long[] txnFees, long timestamp) {
        int txnVolume = txn.getVolume();
        //不接收这个成交回报
        if ( (getVolume(OdrVolume.ReqVolume)-getVolume(OdrVolume.TradeVolume))<txnVolume ) {
            return false;
        }
        transactions.add(txn);
        transactionIds.add(txn.getId());
        int tradeVolume = addVolume(OdrVolume.TradeVolume, txnVolume);
        boolean stateChangedToComplete = false;
        if ( getVolume(OdrVolume.ReqVolume) == tradeVolume ) {
            //全部成交, 切换状态到Complete
            changeState(new OrderStateTuple(OrderState.Complete, OrderSubmitState.Accepted, timestamp, "全部成交"));
            stateChangedToComplete = true;
        }else {
            //部分成交, 切换状态到ParticallyComplete
            changeState(new OrderStateTuple(OrderState.ParticallyComplete, OrderSubmitState.Accepted, timestamp, "部分成交"));
        }

        if ( getOffsetFlags()==OrderOffsetFlag.OPEN) {
            //开仓--需要计算资金变动
            //计算平均开仓价
            long totalCost = 0;
            for(int i=0;i<transactions.size();i++) {
                Transaction txn0 = transactions.get(i);
                totalCost += txn0.getPrice()*txn0.getVolume();
            }
            setMoney(OdrMoney.OpenCost, totalCost/tradeVolume);

            //调整保证金: 已用/解冻
            addMoney(OdrMoney.LocalUsedMargin, txnFees[0]);
            if ( stateChangedToComplete ) {
                setMoney(OdrMoney.LocalUnfrozenMargin, getMoney(OdrMoney.LocalFrozenMargin));
            } else {
                addMoney(OdrMoney.LocalUnfrozenMargin, txnFees[0]);
            }
        } else {
            //如果是平仓, 需要扣除冻结仓位
            if ( txn.getDirection()==OrderDirection.Sell ) {
                //平多
                addVolume(OdrVolume.LongUnfrozen, txnVolume);
            } else {
                //平空
                addVolume(OdrVolume.ShortUnfrozen, txnVolume);
            }
            //平仓只在Position中调整保证金
        }
        //调整手续费
        addMoney(OdrMoney.LocalUsedCommission, txnFees[1]);
        if ( stateChangedToComplete ) {
            //报单完成, 更新资金变动
            setMoney(OdrMoney.LocalUnfrozenCommission, getMoney(OdrMoney.LocalFrozenCommission));
        } else {
            addMoney(OdrMoney.LocalUnfrozenCommission, txnFees[1]);
        }
        return true;
    }

    public static OrderImpl load(BORepository repository, String orderId, String data){
        OrderImpl result = (OrderImpl)cacheGet(orderId);
        if ( null==result ) {
            String json = data;
            if (null==json) {
                json = repository.load(BOEntityType.Order, orderId);
            }
            if ( !StringUtil.isEmpty(json) ) {
                result = new OrderImpl(repository, (JsonObject)JsonParser.parseString(json));
                cachePut(result);
            }
        }
        return result;
    }

    public static List<OrderImpl> loadAll(BORepository repository, String accountId, LocalDate tradingDay){
        StringBuilder queryExpr = new StringBuilder();
        SQLListExpr listExpr = new SQLListExpr();
        for(var state:OrderState.values()) {
            if (state.isDone()) {
                listExpr.addItem(new SQLCharExpr(state.name()));
            }
        }
        queryExpr.append("accountId=").append(new SQLCharExpr(accountId));
        queryExpr.append(" AND (state NOT IN ").append(listExpr).append(" OR tradingDay=").append(new SQLCharExpr(DateUtil.date2str(tradingDay))).append(")");
        BOEntityIterator entityIt = repository.search(BOEntityType.Order, queryExpr.toString());
        List<OrderImpl> result = new ArrayList<>();
        while(entityIt.hasNext()) {
            String odrId=entityIt.next();
            String odrData = entityIt.getData();
            OrderImpl order = load(repository, odrId, odrData);
            //非当天报单自动取消
            if ( !tradingDay.equals(order.getTradingDay()) ){
                order.changeState(new OrderStateTuple(OrderState.Canceled, OrderSubmitState.Accepted, 0));
                repository.asynSave(BOEntityType.Order, odrId, order.toJson());
            }
            result.add(order);
        }
        return result;
    }

}
