package trader.simulator.trade;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.eclipse.jetty.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import trader.common.beans.BeansContainer;
import trader.common.exception.AppException;
import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableTradingTimes;
import trader.common.util.ConversionUtil;
import trader.common.util.DateUtil;
import trader.common.util.FileUtil;
import trader.common.util.JsonEnabled;
import trader.common.util.JsonUtil;
import trader.common.util.PriceUtil;
import trader.service.ServiceConstants.ConnState;
import trader.service.md.MarketData;
import trader.service.md.MarketDataListener;
import trader.service.md.MarketDataService;
import trader.service.trade.Account;
import trader.service.trade.FutureFeeEvaluator;
import trader.service.trade.Order;
import trader.service.trade.OrderBuilder;
import trader.service.trade.OrderStateTuple;
import trader.service.trade.TradeConstants;
import trader.service.trade.TxnFeeEvaluator;
import trader.service.trade.spi.AbsTxnSession;
import trader.service.trade.spi.TxnSessionListener;
import trader.simulator.SimMarketTimeAware;
import trader.simulator.SimMarketTimeService;
import trader.simulator.trade.SimOrder.SimOrderState;
import trader.simulator.trade.SimResponse.ResponseType;

/**
 * 模拟行情连接
 */
public class SimTxnSession extends AbsTxnSession implements JsonEnabled, TradeConstants, SimMarketTimeAware, MarketDataListener {
    private final static Logger logger = LoggerFactory.getLogger(SimTxnSession.class);

    private MarketDataService mdService;
    private long[] money = new long[AccMoney.values().length];
    private SimMarketTimeService mtService;
    private Map<Exchangeable, SimPosition> positions = new HashMap<>();
    private List<SimOrder> orders = new ArrayList<>();
    private List<SimTxn> allTxns = new ArrayList<>();
    private List<SimResponse> pendingResponses = new ArrayList<>();
    private TxnFeeEvaluator feeEvaluator;

    public SimTxnSession(BeansContainer beansContainer, Account account, TxnSessionListener listener) {
        super(beansContainer, account, listener);
        mdService = beansContainer.getBean(MarketDataService.class);
        mdService.addListener(this);
        mtService = beansContainer.getBean(SimMarketTimeService.class);
        mtService.addListener(this);
    }

    @Override
    public String getProvider() {
        return "simtxn";
    }

    public AccClassification getClassification() {
        return AccClassification.Future;
    }

    @Override
    public LocalDate getTradingDay() {
        return tradingDay;
    }

    public TxnFeeEvaluator getFeeEvaluator() {
        return feeEvaluator;
    }

    public MarketData getLastMarketData(Exchangeable e) {
        return mdService.getLastData(e);
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();

        json.addProperty("tradingDay", DateUtil.date2str(tradingDay));
        json.add("money", TradeConstants.accMoney2json(money));
        JsonObject posJson = new JsonObject();
        for(SimPosition pos:positions.values()) {
            posJson.add(pos.getInstrument().id(), JsonUtil.object2json(pos));
        }
        json.add("positions", posJson);
        json.add("orders", JsonUtil.object2json(orders));
        return json;
    }

    @Override
    public String toString() {
        return toJson().toString();
    }

    public long[] getMoney() {
        long[] money = new long[this.money.length];
        System.arraycopy(this.money, 0, money, 0, money.length);
        return money;
    }

    public long getMoney(AccMoney money) {
        return this.money[money.ordinal()];
    }

    private void setMoney(AccMoney money, long value) {
        this.money[money.ordinal()] = value;
    }

    private void addMoney(AccMoney money, long value) {
        this.money[money.ordinal()] += value;
    }

    @Override
    public void connect(Properties connProps) {
        changeState(ConnState.Connecting);
        try {
            tradingDay = mtService.getTradingDay();
            String commissionsFile = connProps.getProperty("commissionsFile");
            feeEvaluator = FutureFeeEvaluator.fromJson(null, (JsonObject)(new JsonParser()).parse(FileUtil.read(new File(commissionsFile))));
            //从KVStore加载数据
            if ( !loadData() ) {
                initData(connProps);
            }
            changeState(ConnState.Connected);
        } catch (Throwable t) {
            logger.error("Connect failed", t);
            changeState(ConnState.ConnectFailed);
        }
    }

    @Override
    public String syncLoadFeeEvaluator(Collection<Exchangeable> subscriptions) throws Exception
    {
        return feeEvaluator.toJson().toString();
    }

    @Override
    public String syncConfirmSettlement() throws Exception {
        return null;
    }

    @Override
    public long[] syncQryAccounts() throws Exception {
        long[] result = new long[money.length];
        System.arraycopy(money, 0, result, 0, result.length);
        return result;
    }

    @Override
    public String syncQryPositions() throws Exception {
        JsonObject posInfos = new JsonObject();
        for(Exchangeable e:positions.keySet()) {
            posInfos.add(e.toString(), positions.get(e).toJson());
        }
        return posInfos.toString();
    }

    public String syncQryOrders() throws Exception{
        return (new JsonArray()).toString();
    }

    @Override
    public void asyncSendOrder(Order order0) throws AppException
    {
        Exchangeable instrument = order0.getInstrument();
        SimOrder order = new SimOrder(order0, mtService.getMarketTime());
        checkNewOrder(order);
        orders.add(order);
        long currTime= DateUtil.localdatetime2long(order0.getInstrument().exchange().getZoneId(), mtService.getMarketTime());
        if ( order.getState()==SimOrderState.Placed ) {
            SimPosition pos = getPosition(instrument, true);
            pos.addOrder(order);
            //更新账户数据
            //listener.changeOrderState(order0, new OrderStateTuple(OrderState.Submitting, OrderSubmitState.InsertSubmitting, currTime), null);
            listener.onOrderStateChanged(order0, new OrderStateTuple(OrderState.Submitted, OrderSubmitState.InsertSubmitted, currTime), null);
            pos.updateOnMarketData(mdService.getLastData(instrument).lastPrice);
            updateAccount();
            respondLater(instrument, ResponseType.RtnOrder, order, new OrderStateTuple(OrderState.Accepted, OrderSubmitState.Accepted, currTime+2, "未成交"));
        }else {
            respondLater(instrument, ResponseType.ErrRtnOrderInsert, order, new OrderStateTuple(OrderState.Failed, OrderSubmitState.InsertRejected, currTime+2, "报单失败"));
        }
    }

    @Override
    public void asyncCancelOrder(Order order0) throws AppException {
        Exchangeable instrument = order0.getInstrument();
        SimOrder order = null;
        SimPosition pos = getPosition(instrument, false);
        if ( pos!=null ) {
            order = pos.removeOrder(order0.getRef());
        }
        if ( order!=null ) {
            long currTime= DateUtil.localdatetime2long(order0.getInstrument().exchange().getZoneId(), mtService.getMarketTime());
            listener.onOrderStateChanged(order0, new OrderStateTuple(OrderState.Accepted, OrderSubmitState.CancelSubmitted, currTime), null);
            cancelOrder(order);
            //更新账户数据
            pos.updateOnMarketData(mdService.getLastData(instrument).lastPrice);
            updateAccount();
            respondLater(instrument, ResponseType.RtnOrder, order, new OrderStateTuple(OrderState.Canceled, OrderSubmitState.Accepted, currTime+2, "已撤单"));
        }else {
            //返回无对应报单错误
            respondLater(instrument, ResponseType.RspOrderAction, order0);
        }
    }

    @Override
    public void asyncModifyOrder(Order order0, OrderBuilder builder) throws AppException {
        Exchangeable instrument = order0.getInstrument();
        SimOrder order = null;
        SimPosition pos = positions.get(instrument);
        if ( pos!=null ) {
            order = pos.getOrder(order0.getRef());
        }
        if ( order!=null ) {
            long currTime= DateUtil.localdatetime2long(order0.getInstrument().exchange().getZoneId(), mtService.getMarketTime());
            order.modify(builder);
            respondLater(instrument, ResponseType.RtnOrder, order, new OrderStateTuple(OrderState.Accepted, OrderSubmitState.ModifySubmitted, currTime));
        }else {
            //返回无对应报单错误
            respondLater(instrument, ResponseType.RspOrderAction, order0);
        }
    }

    @Override
    protected void closeImpl() {
        //保存数据到KVStore
        saveData();
        changeState(ConnState.Disconnected);
    }

    @Override
    public void onMarketData(MarketData md) {
        if (tradingDay==null) {
            tradingDay = DateUtil.str2localdate(md.tradingDay);
        }
        SimPosition pos = positions.get(md.instrument);
        if ( pos!=null ) {
            List<SimOrder> orders = new ArrayList<>(pos.getOrders());
            for(SimOrder order:orders) {
                SimTxn txn = completeOrder(order, md);
                if ( txn!=null ) {
                    pos.updateOnTxn(txn, md.updateTime);
                    long currTime= md.updateTimestamp;
                    respondLater(order.getInstrument(), ResponseType.RtnOrder, order, new OrderStateTuple(OrderState.Complete, OrderSubmitState.Accepted, currTime, "全部成交"));
                    respondLater(order.getInstrument(), ResponseType.RtnTrade, txn);
                }
            }
            pos.updateOnMarketData(md.lastPrice);
        }
        updateAccount();
        sendResponses();
    }

    @Override
    public void onTimeChanged(LocalDate tradingDay, LocalDateTime actionTime) {
        sendResponses();
    }

    /**
     * 加载数据
     */
    private boolean loadData() {
        String jsonText = null; //kvStore.getAsString("simTxn");
        if ( StringUtil.isEmpty(jsonText)) {
            return false;
        }
        JsonObject json = (new JsonParser()).parse(jsonText).getAsJsonObject();

        long balance = PriceUtil.str2long(json.get("balance").getAsString());
        setMoney(AccMoney.Balance, balance);
        setMoney(AccMoney.BalanceBefore, balance);
        JsonArray jsonPos = json.get("positions").getAsJsonArray();
        long margin = 0, posprofit=0;
        for(int i=0;i<jsonPos.size();i++) {
            SimPosition pos = SimPosition.loadFromJson(this, jsonPos.get(i).getAsJsonObject());
            positions.put(pos.getInstrument(), pos);
            margin += pos.getMoney(PosMoney.UseMargin);
            posprofit += pos.getMoney(PosMoney.PositionProfit);
        }
        setMoney(AccMoney.CurrMargin, margin);
        setMoney(AccMoney.PositionProfit, posprofit);
        setMoney(AccMoney.Available, balance-margin);

        return true;
    }

    private void initData(Properties connProps) {
        double initMoney = ConversionUtil.toDouble(connProps.getProperty("initMoney"), true);
        if ( initMoney==0.0 ) {
            initMoney = 50000.00;
        }
        money[TradeConstants.AccMoney.Balance.ordinal()] = PriceUtil.price2long(initMoney);
        money[TradeConstants.AccMoney.BalanceBefore.ordinal()] = PriceUtil.price2long(initMoney);
        money[TradeConstants.AccMoney.Available.ordinal()] = PriceUtil.price2long(initMoney);
    }

    /**
     * 保存数据
     */
    private void saveData() {
        JsonObject json = new JsonObject();
        json.addProperty("balance", PriceUtil.long2str(getMoney(AccMoney.Balance)));
        JsonArray posJson = new JsonArray();
        for(SimPosition pos:positions.values()) {
            int position = pos.getVolume(PosVolume.Position);
            int longPos = pos.getVolume(PosVolume.LongPosition);
            int shortPos = pos.getVolume(PosVolume.ShortPosition);
            if ( position==0 && longPos==0 && shortPos==0 ) {
                continue;
            }
            posJson.add(pos.toJson());
        }
        json.add("positions", posJson);
        //kvStore.put("simTxn", json.toString());
    }

    private void respondLater(Exchangeable e, ResponseType responseType, Object ...data) {
        pendingResponses.add(new SimResponse(e, responseType, data));
    }

    /**
     * 实际发送通知
     */
    private void sendResponses() {
        for(SimResponse r:pendingResponses) {
            long currTime = DateUtil.localdatetime2long(r.getInstrument().exchange().getZoneId(), mtService.getMarketTime());
            switch(r.getType()) {
            case RspOrderInsert:
            {
                SimOrder order = (SimOrder)r.getData()[0];
                OrderStateTuple stateTuple = (OrderStateTuple)r.getData()[1];
                listener.onOrderStateChanged(listener.getOrderByRef(order.getRef()), stateTuple, null);
            }
            break;
            case RspOrderAction:
            {
                Order order = (Order)r.getData()[0];
                listener.onOrderStateChanged(listener.getOrderByRef(order.getRef()), new OrderStateTuple(OrderState.Failed, OrderSubmitState.CancelRejected, currTime, "取消失败"), null);
            }
            break;
            case RtnOrder:
            {
                SimOrder order = (SimOrder)r.getData()[0];
                OrderStateTuple stateTuple = (OrderStateTuple)r.getData()[1];
                Map<String, String> attrs = new HashMap<>();
                attrs.put(Order.ODRATR_CTP_SYS_ID, order.getSysId());
                listener.onOrderStateChanged(listener.getOrderByRef(order.getRef()), stateTuple, attrs);
            }
            break;
            case RtnTrade:
            {
                SimTxn txn = (SimTxn)r.getData()[0];
                Exchangeable instrument = txn.getOrder().getInstrument();
                assert(instrument!=null);
                listener.onTransaction(
                        txn.getId(),
                        instrument,
                        txn.getOrder().getRef(),
                        txn.getOrder().getDirection(),
                        txn.getOrder().getOffsetFlag(),
                        txn.getPrice(),
                        txn.getVolume(),
                        currTime,
                        txn
                        );
            }
            break;
            case ErrRtnOrderInsert:
            {
                SimOrder order = (SimOrder)r.getData()[0];
                OrderStateTuple stateTuple = (OrderStateTuple)r.getData()[1];
                listener.onOrderStateChanged(listener.getOrderByRef(order.getRef()), stateTuple, null);
            }
            break;
            default:
                logger.error("Unsupported response event type: "+r.getType());
                break;
            }
        }
        pendingResponses.clear();
    }

    /**
     * 校验新报单
     */
    private void checkNewOrder(SimOrder order) {
        Exchangeable instrument = order.getInstrument();
        MarketData lastMd = mdService.getLastData(instrument);
        //检查Volume
        if ( order.getVolume()==0) {
            order.setState(SimOrderState.Invalid, mtService.getMarketTime());
            order.setErrorReason("Volume为0");
            return;
        }
        //检查开市
        LocalDateTime time = mtService.getMarketTime();
        ExchangeableTradingTimes tradingTimes = instrument.exchange().getTradingTimes(instrument, mtService.getTradingDay());
        switch( tradingTimes.getTimeStage(time) ) {
        case AggregateAuction:
        case MarketOpen:
            break;
        default:
            order.setState(SimOrderState.Invalid, mtService.getMarketTime());
            order.setErrorReason(instrument+" 非交易时段");
            return;
        }
        //检查是否有行情
        if ( lastMd==null ) {
            order.setState(SimOrderState.Invalid, mtService.getMarketTime());
            order.setErrorReason(instrument+" 不交易, 无行情数据");
            return;
        }
        //检查是否在价格最高最低范围内
        if ( order.getLimitPrice()<lastMd.lowerLimitPrice || order.getLimitPrice()>lastMd.upperLimitPrice ) {
            order.setState(SimOrderState.Invalid, mtService.getMarketTime());
            order.setErrorReason(PriceUtil.long2str(order.getLimitPrice())+" 超出报价范围");
            return;
        }
        //检查报单价格满足priceTick需求
        long priceTick = feeEvaluator.getPriceTick(instrument);
        if ( (order.getLimitPrice()%priceTick)!=0 ) {
            order.setState(SimOrderState.Invalid, mtService.getMarketTime());
            order.setErrorReason(PriceUtil.long2str(order.getLimitPrice())+" 报价TICK不对");
            return;
        }
        //检查保证金需求
        long[] values = feeEvaluator.compute(instrument, order.getVolume(), (order.getLimitPrice()), order.getDirection(), order.getOffsetFlag());
        long frozenMargin = values[0];
        long frozenCommissions = values[1];

        if ( money[AccMoney.Available.ordinal()]<(frozenMargin+frozenCommissions+PriceUtil.price2long(10.00)) ) {
            order.setState(SimOrderState.Invalid, mtService.getMarketTime());
            order.setErrorReason("资金不足: "+PriceUtil.long2str(money[AccMoney.Available.ordinal()]));
            return;
        }
        //平仓报单检查持仓
        if ( order.getOffsetFlag()!=OrderOffsetFlag.OPEN ) {
            int posAvail = 0;
            SimPosition pos = positions.get(instrument);
            if ( pos!=null ) {
                if ( order.getDirection()==OrderDirection.Sell ) {
                    //卖出平多
                    posAvail = pos.getVolume(PosVolume.LongPosition) - pos.getVolume(PosVolume.LongFrozen);
                }else {
                    //买入平空
                    posAvail = pos.getVolume(PosVolume.ShortPosition) - pos.getVolume(PosVolume.ShortFrozen);
                }
            }
            if ( posAvail<order.getVolume() ) {
                order.setState(SimOrderState.Invalid, mtService.getMarketTime());
                order.setErrorReason("平仓量超过持仓量: "+order.getVolume());
                return;
            }
        }
        order.setState(SimOrderState.Placed, mtService.getMarketTime());
    }

    /**
     * 更新账户的可用资金等
     */
    private void updateAccount() {
        long totalUseMargins = 0, totalFrozenMargins=0, totalFrozenCommission=0, totalPosProfit=0, totalCommission=0, totalCloseProfit=0;
        for(SimPosition p:this.positions.values()) {
            totalUseMargins += p.getMoney(PosMoney.UseMargin);
            totalFrozenMargins += p.getMoney(PosMoney.FrozenMargin);
            totalFrozenCommission += p.getMoney(PosMoney.FrozenCommission);
            totalPosProfit += p.getMoney(PosMoney.PositionProfit);
            totalCommission += p.getMoney(PosMoney.Commission);
            totalCloseProfit += p.getMoney(PosMoney.CloseProfit);
        }

        setMoney(AccMoney.Commission, totalCommission);
        setMoney(AccMoney.CloseProfit, totalCloseProfit);
        long balance = money[AccMoney.BalanceBefore.ordinal()] - money[AccMoney.Commission.ordinal()] + totalPosProfit + money[AccMoney.CloseProfit.ordinal()];
        setMoney(AccMoney.Balance, balance);
        setMoney(AccMoney.Available, balance - totalUseMargins - totalFrozenMargins - totalFrozenCommission);
        setMoney(AccMoney.FrozenMargin, totalFrozenMargins);
        setMoney(AccMoney.CurrMargin, totalUseMargins);
        setMoney(AccMoney.FrozenCommission, totalFrozenCommission);
        setMoney(AccMoney.PositionProfit, totalPosProfit);
    }

    /**
     * 取消报单
     */
    private void cancelOrder(SimOrder order) {
        order.setState(SimOrderState.Canceled, mtService.getMarketTime());
    }

    /**
     * 根据最新行情成交报单
     */
    private SimTxn completeOrder(SimOrder order, MarketData md) {
        SimTxn result = null;
        long txnPrice = 0;
        if ( order.getState()==SimOrderState.Placed ) {
            long orderPrice = order.getLimitPrice();
            switch(order.getPriceType()) {
            case LimitPrice:
                if ( order.getDirection()==OrderDirection.Buy && orderPrice>=md.lastAskPrice() ) {
                    txnPrice = orderPrice;
                }
                if (order.getDirection()==OrderDirection.Sell && orderPrice<=md.lastBidPrice() ){
                    txnPrice = orderPrice;
                }
                break;
            case BestPrice:
            case AnyPrice:
                if ( order.getDirection()==OrderDirection.Buy ) {
                    txnPrice = md.lastAskPrice()+md.instrument.getPriceTick();
                } else if (order.getDirection()==OrderDirection.Sell ) {
                    txnPrice = md.lastBidPrice()-md.instrument.getPriceTick();
                }
                break;
            }
        }
        if ( txnPrice!=0 ) {
            result = new SimTxn(order, txnPrice, mtService.getMarketTime());
            order.setState(SimOrderState.Completed, mtService.getMarketTime());
            allTxns.add(result);
            assert(result.getVolume()>0);
        }
        return result;
    }

    private SimPosition getPosition(Exchangeable instrument, boolean autoCreate) {
        SimPosition pos = positions.get(instrument);
        if ( pos==null && autoCreate) {
            pos = new SimPosition(this, instrument);
            positions.put(instrument, pos);
        }
        return pos;
    }

    /**
     * 将资金从moneyIdx转移到moneyIdx2下, 在扣除保证金时有用.
     * 如果moneyIdx的资金小于amount, 失败.
     */
    boolean transferMoney(int moneyIdx, int moneyIdx2, long amount) {
        if ( money[moneyIdx]<amount ) {
            return false;
        }
        money[moneyIdx] -= amount;
        money[moneyIdx2] += amount;
        return true;
    }

    @Override
    public Collection<Exchangeable> syncQueryInstruments() throws Exception {
        return Collections.emptyList();
    }

}
