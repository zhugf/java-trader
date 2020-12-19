package trader.service.trade;

import java.io.File;
import java.io.StringReader;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.core.FileAppender;
import trader.common.beans.BeansContainer;
import trader.common.exception.AppException;
import trader.common.exchangeable.Exchangeable;
import trader.common.util.ConversionUtil;
import trader.common.util.DateUtil;
import trader.common.util.FileUtil;
import trader.common.util.IniFile;
import trader.common.util.JsonUtil;
import trader.common.util.PriceUtil;
import trader.common.util.StringUtil;
import trader.common.util.TraderHomeUtil;
import trader.common.util.UUIDUtil;
import trader.service.ServiceConstants.AccountState;
import trader.service.ServiceConstants.ConnState;
import trader.service.ServiceErrorConstants;
import trader.service.md.MarketData;
import trader.service.md.MarketDataListener;
import trader.service.md.MarketDataService;
import trader.service.repository.BOEntity;
import trader.service.repository.BOEntityIterator;
import trader.service.repository.BORepository;
import trader.service.repository.BORepositoryConstants.BOEntityType;
import trader.service.trade.spi.AbsTxnSession;
import trader.service.trade.spi.TxnSessionListener;

/**
 * 一个交易账户和通道实例对象.
 * <BR>每个Account对象实例有自己的RingBuffer, 有独立的Log文件, 有独立的多线程处理策略.
 * <BR>每个交易策略实例是运行在独立的线程中, 使用disruptor作为独立的调度
 */
public class AccountImpl implements Account, TxnSessionListener, TradeConstants, ServiceErrorConstants, MarketDataListener {

    private String id;
    private BeansContainer beansContainer;
    private BORepository repository;
    private String loggerCategory;
    private Logger logger;
    private File tradingWorkDir;
    private long[] money = new long[AccMoney.values().length];
    private AccountState state;
    private MarketTimeService mtService;
    private TradeService tradeService;
    private AbsTxnSession txnSession;
    private TxnFeeEvaluator feeEvaluator;
    private Properties connectionProps;
    private List<AccountListener> listeners = new ArrayList<>();
    private Map<Exchangeable, PositionImpl> positions = new HashMap<>();
    private Map<String, OrderImpl> ordersByRef = new ConcurrentHashMap<>();
    private Map<String, OrderImpl> ordersById = new ConcurrentHashMap<>();
    private List<TransactionImpl> txns = new ArrayList<>(100);
    private LinkedList<OrderImpl> orders = new LinkedList<>();
    private Map<Exchangeable, AtomicInteger> cancelCounts = new ConcurrentHashMap<>();
    private Lock orderLock = new ReentrantLock();
    private Lock positionLock = new ReentrantLock();

    public AccountImpl(TradeService tradeService, BeansContainer beansContainer, Map configElem) {
        this.tradeService = tradeService;
        this.beansContainer = beansContainer;
        id = ConversionUtil.toString(configElem.get("id"));
        state = AccountState.Created;
        String provider = ConversionUtil.toString(configElem.get("provider"));
        repository = beansContainer.getBean(BORepository.class);
        mtService = beansContainer.getBean(MarketTimeService.class);
        LocalDate tradingDay = mtService.getTradingDay();
        tradingWorkDir = new File(TraderHomeUtil.getDirectory(TraderHomeUtil.DIR_WORK), DateUtil.date2str(tradingDay));
        createAccountLogger();
        update(configElem);
        txnSession = createTxnSession(provider);
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public AccClassification getClassification() {
        return txnSession.getClassification();
    }

    @Override
    public long getMoney(AccMoney mny) {
        return this.money[mny.ordinal()];
    }

    public long[] getMoneys() {
        long result[] = new long[money.length];
        System.arraycopy(money, 0, result, 0, result.length);
        return result;
    }

    public long addMoney(AccMoney mny, long toAdd) {
        money[mny.ordinal()] += toAdd;
        return money[mny.ordinal()];
    }

    public long setMoney(AccMoney mny, long value) {
        long result = money[mny.ordinal()];
        money[mny.ordinal()] = value;
        return result;
    }

    /**
     * 将资金从mny转移到mny2下, 在扣除保证金时有用.
     * 如果mny的资金小于amount, 失败.
     */
    boolean transferMoney(AccMoney mny, AccMoney mny2, long amount) {
        if ( money[mny.ordinal()]<amount ) {
            return false;
        }
        money[mny.ordinal()] -= amount;
        money[mny2.ordinal()] += amount;
        return true;
    }

    @Override
    public AccountState getState() {
        return state;
    }

    public Properties getConnectionProps() {
        return connectionProps;
    }

    @Override
    public TxnSession getSession() {
        return txnSession;
    }

    @Override
    public TxnFeeEvaluator getFeeEvaluator() {
        return feeEvaluator;
    }

    @Override
    public List<Order> getOrders() {
        return Collections.unmodifiableList(orders);
    }

    @Override
    public Order getOrderByRef(String orderRef) {
        return ordersByRef.get(orderRef);
    }

    public Order getOrder(String orderId) {
        return ordersById.get(orderId);
    }

    @Override
    public Collection<Position> getPositions() {
        return new ArrayList<>(positions.values());
    }

    public Collection<Transaction> getTransactions(){
        return Collections.unmodifiableCollection(txns);
    }

    @Override
    public Position getPosition(Exchangeable instrument) {
        return positions.get(instrument);
    }

    @Override
    public int getCancelCount(Exchangeable instrument) {
        AtomicInteger i = cancelCounts .get(instrument);
        if ( i==null ) {
            return 0;
        }
        return i.get();
    }

    @Override
    public void addAccountListener(AccountListener listener) {
        if ( listener!=null && !listeners.contains(listener)) {
            List<AccountListener> v = new ArrayList<>(listeners);
            v.add(listener);
            listeners = v;
        }
    }

    @Override
    public void removeAccountListener(AccountListener listener) {
        if ( listener!=null && listeners.contains(listener)) {
            List<AccountListener> v = new ArrayList<>(listeners);
            v.remove(listener);
            listeners = v;
        }
    }

    @Override
    public Order createOrder(OrderBuilder builder) throws AppException {
        if ( txnSession==null || txnSession.getState()!=ConnState.Connected ) {
            throw new AppException(ERRCODE_TRADE_SESSION_NOT_READY, "Account "+getId()+" txn session is not ready");
        }
        long[] localOrderMoney = (new OrderValidator(beansContainer, this, builder)).validate();
        //创建Order
        Exchangeable e = builder.getInstrument();
        String orderId = BOEntity.ID_PREFIX_ORDER+UUIDUtil.genUUID58();
        String orderRef = tradeService.getOrderRefGen().nextRefId(id);
        OrderImpl order = new OrderImpl(orderId, this, mtService.getTradingDay(), orderRef, builder, null);
        if ( logger.isInfoEnabled() ) {
            logger.info("报单 "+order.toString());
        }
        PositionImpl pos = null;
        orderLock.lock();
        try {
            ordersByRef.put(orderRef, order);
            ordersById.put(orderId, order);
            orders.add(order);
        }finally {
            orderLock.unlock();
        }
        synchronized(order) {
            try {
                //关联Position
                pos = getOrCreatePosition(e, true);
                //本地计算和冻结仓位和保证金
                positionLock.lock();
                try {
                    if ( order.getOffsetFlags()==OrderOffsetFlag.OPEN ) {
                        order.setMoney(OdrMoney.LocalFrozenMargin, localOrderMoney[OdrMoney.LocalFrozenMargin.ordinal()]);
                    }
                    order.setMoney(OdrMoney.LocalFrozenCommission, localOrderMoney[OdrMoney.LocalFrozenCommission.ordinal()]);
                    order.setMoney(OdrMoney.PriceCandidate, localOrderMoney[OdrMoney.PriceCandidate.ordinal()]);
                    localOrderFreeze(order);
                    //仓位管理
                    pos.localFreeze(order);
                }finally {
                    positionLock.unlock();
                }
                //异步发送
                txnSession.asyncSendOrder(order);
                return order;
            }catch(AppException t) {
                //回退本地已冻结资金和仓位
                if ( order.getStateTuple()==OrderStateTuple.STATE_UNKNOWN ) {
                    OrderStateTuple newState = new OrderStateTuple(OrderState.Failed, OrderSubmitState.Unsubmitted, System.currentTimeMillis(), t.toString());
                    onOrderStateChanged(order, newState, null);
                }
                logger.error("报单错误 "+t.toString()+" : "+order, t);
                throw t;
            }
        }
    }

    @Override
    public boolean cancelOrder(String orderId) throws AppException
    {
        //取消订单前检查
        OrderImpl order = ordersById.get(orderId);
        if ( order==null ) {
            throw new AppException(ERRCODE_TRADE_ORDER_NOT_FOUND, "Account "+getId()+" not found order "+orderId);
        }
        Exchangeable e = order.getInstrument();
        boolean result = false;
        synchronized(order) {
            OrderStateTuple stateTuple = order.getStateTuple();
            OrderSubmitState odrSubmitState = stateTuple.getSubmitState();
            if ( stateTuple.getState().isRevocable()
                    && !odrSubmitState.isSubmitting()
                    && odrSubmitState!=OrderSubmitState.CancelSubmitted )
            {
                PositionImpl pos = getOrCreatePosition(e, true);
                txnSession.asyncCancelOrder(order);
                result = true;
            }
        }
        return result;
    }

    @Override
    public boolean modifyOrder(String orderId, OrderBuilder builder) throws AppException {
        OrderImpl order = ordersById.get(orderId);
        if ( order==null ) {
            throw new AppException(ERRCODE_TRADE_ORDER_NOT_FOUND, "Account "+getId()+" not found orde "+orderId);
        }

        boolean result = false;
        synchronized(order) {
            OrderStateTuple stateTuple = order.getStateTuple();
            OrderSubmitState odrSubmitState = stateTuple.getSubmitState();
            if ( stateTuple.getState().isRevocable()
                    && !odrSubmitState.isSubmitting()
                    && odrSubmitState!=OrderSubmitState.ModifySubmitted )
            {
                txnSession.asyncModifyOrder(order, builder);
                logger.info("Order "+order.getRef()+" is modified, new limitPrice: "+PriceUtil.long2str(builder.getLimitPrice())+", old: "+PriceUtil.long2str(order.getLimitPrice()));
                order.setLimitPrice(builder.getLimitPrice());
                result = true;
            }
        }
        return result;
    }

    /**
     * 账户初始工作, 确认结算单, 加载账户持仓, 订单等信息.
     * <BR>会在一个独立的线程中执行
     */
    public void init() {
        changeState(AccountState.Initialzing);

        long t0 = System.currentTimeMillis();
        try{
            //查询并确认结算单
            String settlement = txnSession.syncConfirmSettlement();
            if ( !StringUtil.isEmpty(settlement)) {
                logger.info("Account "+getId()+" settlement: \n"+settlement);
                File settlementFile = new File(TraderHomeUtil.getDirectory(TraderHomeUtil.DIR_WORK), getId()+"-"+DateUtil.date2str(mtService.getTradingDay())+".txt");
                FileUtil.save(settlementFile, settlement);
            }
            //查询账户
            money = txnSession.syncQryAccounts();
            //查询持仓
            positions = loadPositions();
            //加载品种的交易数据
            if ( null==feeEvaluator ) {
                loadFeeEvaluator();
            }
            long t1 = System.currentTimeMillis();
            changeState(AccountState.Ready);
            logger.info("Account "+getId()+" initialize in "+(t1-t0)+" ms");
        }catch(Throwable t) {
            logger.error("Account "+getId()+" initialize failed", t);
            changeState(AccountState.NotReady);
        }
    }

    public void destroy() {
        if ( this.txnSession!=null ) {
            this.txnSession.close();
        }
    }

    /**
     * 更新配置属性
     * @return true 如果有变化, false 如果相同
     */
    public boolean update(Map configElem) {
        boolean result = false;
        IniFile configIni = null;
        try{
            configIni = new IniFile(new StringReader((String)configElem.get("text")));
        }catch(Throwable t) {
            throw new RuntimeException(t);
        }
        Properties connectionProps2 = configIni.getSection("connectionProps").getProperties();
        if ( !connectionProps2.equals(connectionProps) ) {
            this.connectionProps = connectionProps2;
            result = true;
        }
        return result;
    }

    /**
     * 从BORepository恢复今天的Order/Transaction
     */
    public void restoreFromRepository() {
        String tradingDay = DateUtil.date2str(mtService.getTradingDay());
        BOEntityIterator iter2 = repository.search(BOEntityType.Transaction, "tradingDay='"+tradingDay+"'");
        while( iter2.hasNext() ) {
            iter2.next();
            TransactionImpl txn = (TransactionImpl)iter2.getEntity();
            txns.add(txn);
        }
        BOEntityIterator iter = repository.search(BOEntityType.Order, "tradingDay='"+tradingDay+"'");
        while( iter.hasNext() ) {
            String odrId = iter.next();
            OrderImpl odr = (OrderImpl)iter.getEntity();
            ordersById.put(odr.getId(), odr);
            ordersByRef.put(odr.getRef(), odr);
        }
    }

    @Override
    public String getLoggerCategory() {
        return loggerCategory;
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("classification", getClassification().name());
        json.addProperty("loggerCategory", loggerCategory);
        json.addProperty("state", state.name());
        json.add("txnSession", txnSession.toJson());
        json.add("connectionProps", JsonUtil.object2json(connectionProps));
        json.add("money", TradeConstants.accMoney2json(money));
        json.add("cancelCounts", JsonUtil.object2json(cancelCounts));
        return json;
    }

    @Override
    public String toString() {
        return toJson().toString();
    }

    @Override
    public void onAccountTransfer(AccountTransferAction action, long tradeAmount) {
        long unit=1;
        switch(action) {
        case Deposit:
            addMoney(AccMoney.Deposit, tradeAmount);
            break;
        case Withdraw:
            addMoney(AccMoney.Withdraw, tradeAmount);
            unit = -1;
            break;
        }
        tradeAmount *= unit;
        addMoney(AccMoney.Available, tradeAmount);
        addMoney(AccMoney.Balance, tradeAmount);
        addMoney(AccMoney.WithdrawQuota, tradeAmount);
    }

    @Override
    public void onTxnSessionStateChanged(TxnSession session, ConnState lastState) {
        ConnState state = session.getState();
        switch(state) {
        case Connected:
            //异步初始化账户
            ExecutorService executorService = beansContainer.getBean(ExecutorService.class);
            executorService.execute(()->{
                init();
            });
            break;
        case Disconnected:
        case ConnectFailed:
            changeState(AccountState.NotReady);
            break;
        default:
            break;
        }
    }

    /**
     * 有成交时
     */
    @Override
    public void onTransaction(String txnId, Exchangeable instrument, String orderRef, OrderDirection txnDirection, OrderOffsetFlag txnFlag, long txnPrice, int txnVolume, long txnTime, Object txnData) {
        OrderImpl order = (OrderImpl)getOrderByRef(orderRef);
        if ( order ==null ){
            logger.error("Account "+getId()+" order ref \""+orderRef+"\" is not found for txn id: "+txnId);
            asyncReload();
            return;
        }
        TransactionImpl txn = new TransactionImpl(
                txnId,
                getId(),
                instrument,
                mtService.getTradingDay(),
                order.getId(),
                txnDirection,
                txnFlag,
                txnPrice,
                txnVolume,
                txnTime,
                txnData
                );
        synchronized(order) {
            txns.add(txn);
            onTransaction(order, txn, System.currentTimeMillis());
        }
        if ( null!=repository) {
            repository.asynSave(BOEntityType.Transaction, txnId, txn);
        }
    }

    /**
     * 当报单状态发生变化时回调
     */
    @Override
    public OrderStateTuple onOrderStateChanged(Order order0, OrderStateTuple newState, Map<String, String> attrs)
    {
        if ( order0==null ) {
            logger.error("状态无对应报单: "+newState);
            return null;
        }
        if( newState.getState()==OrderState.Canceled ) {
            incrementCancelCount(order0.getInstrument());
        }
        OrderImpl order = (OrderImpl)order0;
        if ( attrs!=null ) {
            for(Map.Entry<String, String> attrEntry:attrs.entrySet()) {
                order.setAttr(attrEntry.getKey(), attrEntry.getValue());
            }
        }
        if ( newState.getState()==OrderState.Complete ) {
            //报单状态改变为全部成交, 但是尚未收到对应的成交报单? 忽略
            if ( order.getVolume(OdrVolume.TradeVolume)!=order.getVolume(OdrVolume.ReqVolume) ) {
                if ( logger.isDebugEnabled() ) {
                    logger.debug("报单 "+order.getId()+" R "+order.getRef()+" 忽略全部成交状态, 因未收到成交: "+newState);
                }
                return null;
            }
        }
        OrderStateTuple oldState = order.changeState(newState);
        if ( oldState!=null ) {
            logger.info(" 报单 "+order.getId()+" R:"+order.getRef()+" "+order.getInstrument()+" 状态变化: "+newState);
            PositionImpl pos = (PositionImpl)getPosition(order.getInstrument());
            switch(newState.getState()) {
            case Failed: //报单失败, 本地回退冻结仓位和资金
            case Canceled: //报单取消, 本地回退冻结仓位和资金
            case PartiallyDeleted: //部分取消, 本地回退取消部分的冻结仓位和资金
                positionLock.lock();
                try {
                    //首先设置LocalUnfrozenMargin/LocalUnfrozenCommission
                    order.setMoney(OdrMoney.LocalUnfrozenMargin, order.getMoney(OdrMoney.LocalFrozenMargin) );
                    order.setMoney(OdrMoney.LocalUnfrozenCommission, order.getMoney(OdrMoney.LocalFrozenCommission) );
                    long[] unfreezenFees = localOrderUnfreeze(order);
                    if ( pos!=null ) {
                        pos.localUnfreeze(order, unfreezenFees);
                    } else {
                        logger.error("报单 "+order.getId()+" R: "+order.getRef()+" 无对应的仓位");
                    }
                }finally {
                    positionLock.unlock();
                }
                break;
            case Complete: //报单成交, 本地回退冻结仓位和资金的行为由成交回报函数处理
            default:
                break;
            }
            publishOrderStateChanged(order, oldState);
        } else {
            logger.info(getId()+" 报单 "+order.getId()+" R:"+order.getRef()+" 状态无变化: "+order.getStateTuple());
        }
        return oldState;
    }

    @Override
    public Order createOrderFromResponse(JsonObject orderInfo) {
        String orderRef = orderInfo.get("ref").getAsString();
        OrderImpl order = ordersByRef.get(orderRef);
        if ( order==null ) {
            OrderBuilder orderBuilder = new OrderBuilder();
            orderBuilder.setExchagneable(Exchangeable.fromString(orderInfo.get("instrument").getAsString()))
            .setDirection(ConversionUtil.toEnum(OrderDirection.class, orderInfo.get("direction").getAsString()))
            .setPriceType(ConversionUtil.toEnum(OrderPriceType.class, orderInfo.get("priceType").getAsString()))
            .setLimitPrice(PriceUtil.price2long(orderInfo.get("limitPrice").getAsDouble()))
            .setVolume(ConversionUtil.toInt(orderInfo.get("volume")))
            .setOffsetFlag(ConversionUtil.toEnum(OrderOffsetFlag.class, orderInfo.get("offsetFlag").getAsString()))
            ;
            if ( orderInfo.has("attrs") ) {
                JsonObject attrs = orderInfo.getAsJsonObject("attrs");
                for(String key:attrs.keySet()) {
                    orderBuilder.setAttr(key, attrs.get(key).getAsString());
                }
            }
            OrderState orderState = ConversionUtil.toEnum(OrderState.class, orderInfo.get("state").getAsString());
            OrderSubmitState orderSubmitState = ConversionUtil.toEnum(OrderSubmitState.class, orderInfo.get("submitState").getAsString());
            String stateMessage = null;
            if ( orderInfo.has("stateMessage") ){
                stateMessage = orderInfo.get("stateMessage").getAsString();
            }
            OrderStateTuple stateTuple = new OrderStateTuple( orderState, orderSubmitState, System.currentTimeMillis(), stateMessage);
            String orderId = BOEntity.ID_PREFIX_ORDER+UUIDUtil.genUUID58();
            order = new OrderImpl(orderId, this, mtService.getTradingDay(), orderRef, orderBuilder, stateTuple);
            orderLock.lock();
            try {
                PositionImpl pos = getOrCreatePosition(order.getInstrument(), true);
                ordersByRef.put(orderRef, order);
                ordersById.put(orderId, order);
                orders.add(order);
            }finally {
                orderLock.unlock();
            }
            logger.info("报单 "+orderId+" R:"+orderRef+" 从回报创建: "+order);
            publishOrderStateChanged(order, stateTuple);
            if ( null!=repository ) {
                repository.asynSave(BOEntityType.Order, order.getId(), order);
            }
        }
        return order;
    }

    /**
     * 当市场价格发生变化, 更新持仓盈亏
     */
    @Override
    public void onMarketData(MarketData marketData) {
        if ( state!=AccountState.Ready ) {
            return;
        }
        boolean priceChanged = false;
        PositionImpl pos = positions.get(marketData.instrument);
        if( pos!=null ) {
            priceChanged = pos.onMarketData(marketData);
        }
        if ( priceChanged ) {
            updateAccountMoneyOnMarket();
        }
    }

    /**
     * 处理成交回报, 更新本地仓位和资金数据
     */
    void onTransaction(OrderImpl order, TransactionImpl txn, long timestamp) {
        long[] txnFees = feeEvaluator.compute(txn);
        assert(txnFees!=null);
        OrderStateTuple orderOldState = order.getStateTuple();
        if ( !order.attachTransaction(txn, txnFees, timestamp) ) {
            if( logger.isErrorEnabled() ) {
                logger.error("报单 "+order.getId()+" R:"+order.getRef()+" 拒绝成交事件: "+txn.getId()+" "+txn.getDirection()+" P "+PriceUtil.long2price(txn.getPrice())+" V "+txn.getVolume());
            }
            return;
        }
        long txnCommission = txnFees[1];
        positionLock.lock();
        try {
            long[] orderFees = localOrderUnfreeze(order);
            //更新实际手续费
            transferMoney(AccMoney.Available, AccMoney.Commission, txnCommission);
            addMoney(AccMoney.Balance, -1*txnCommission);
            //更新账户保证金占用等等
            PositionImpl position = (PositionImpl)getPosition(order.getInstrument());
            if ( position!=null ) {
                long closeProfit0 = position.getMoney(PosMoney.CloseProfit);
                //更新持仓和资金
                position.onTransaction(order, txn, txnFees, orderFees);
                long txnProfit = position.getMoney(PosMoney.CloseProfit)-closeProfit0;
                //更新平仓利润
                if ( txnProfit!=0 ) {
                    addMoney(AccMoney.CloseProfit, txnProfit);
                }
            } else {
                logger.error("报单 "+order.getId()+" R:"+order.getRef()+" 无对应持仓");
            }
            updateAccountMoneyOnMarket();
        }finally {
            positionLock.unlock();
        }
        //更新
        publishTransaction(order, txn);
        if ( order.getStateTuple().getState()!=orderOldState.getState()) {
            publishOrderStateChanged(order, orderOldState);
        }
    }

    private void loadFeeEvaluator() throws Exception
    {
        Collection<Exchangeable> subscriptions = Collections.emptyList();
        MarketDataService mdService = beansContainer.getBean(MarketDataService.class);
        if ( mdService!=null ) {
            subscriptions = mdService.getSubscriptions();
        }
        if ( tradeService.getType()==TradeServiceType.Simulator ) {
            String commissionsExchange = txnSession.syncLoadFeeEvaluator(subscriptions);
            FutureFeeEvaluator feeEvaluator = FutureFeeEvaluator.fromJson(JsonParser.parseString(commissionsExchange).getAsJsonObject());
            this.feeEvaluator = feeEvaluator;
        } else {
            File commissionsJson = new File(tradingWorkDir, id+".commissions.json");
            if ( commissionsJson.exists() ) {
                feeEvaluator = FutureFeeEvaluator.fromJson(JsonParser.parseString(FileUtil.read(commissionsJson)).getAsJsonObject());
                logger.info("加载缓存 "+feeEvaluator.getInstruments().size()+" 合约手续费");
            }else {
                String commissionsJsonString = txnSession.syncLoadFeeEvaluator(subscriptions);
                FutureFeeEvaluator feeEvaluator = FutureFeeEvaluator.fromJson(JsonParser.parseString(commissionsJsonString).getAsJsonObject());
                this.feeEvaluator = feeEvaluator;
                FileUtil.save(commissionsJson, feeEvaluator.toJson().toString());
            }
        }
    }

    private Map<Exchangeable, PositionImpl> loadPositions() throws Exception
    {
        Map<Exchangeable, PositionImpl> positions = new HashMap<>();
        JsonObject posInfos = (JsonObject)(new JsonParser()).parse(new StringReader(txnSession.syncQryPositions()));
        for(String posKey:posInfos.keySet()) {
            JsonObject posInfo = (JsonObject)posInfos.get(posKey);
            Exchangeable e = Exchangeable.fromString(posKey);
            PosDirection direction = ConversionUtil.toEnum(PosDirection.class, posInfo.get("direction").getAsString());
            int[] volumes = TradeConstants.json2posVolumes((JsonObject)posInfo.get("volumes"));
            long[] money = TradeConstants.json2posMoney((JsonObject)posInfo.get("money"));

            JsonArray detailArray = (JsonArray)posInfo.get("details");
            List<PositionDetailImpl> details = Collections.emptyList();
            if ( detailArray!=null ) {
                details = new ArrayList<>(detailArray.size()+1);
                for(int i=0;i<detailArray.size();i++) {
                    JsonObject detailInfo = (JsonObject)detailArray.get(i);
                    LocalDate openDate = DateUtil.str2localdate(detailInfo.get("openDate").getAsString());
                    boolean today = openDate.equals(mtService.getTradingDay());
                    details.add(new PositionDetailImpl(
                        ConversionUtil.toEnum(PosDirection.class, detailInfo.get("direction").getAsString()),
                        detailInfo.get("volume").getAsInt(),
                        PriceUtil.str2long(detailInfo.get("price").getAsString()),
                        openDate.atStartOfDay(),
                        today
                    ));
                }
            }
            PositionImpl pos = new PositionImpl(this, e, direction, money, volumes, details);
            positions.put(e, pos);
        }
        return positions;
    }

    /**
     * 创建Account Logger
     * @param separateLogger true 会为每个Account创建一个日志文件: TRADER_HOME/work/<TRADING_DAY>/accountId.log
     */
    private void createAccountLogger() {
        if ( tradeService.getType()!=TradeServiceType.Simulator ) {
            LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();

            FileAppender fileAppender = new FileAppender();
            fileAppender.setContext(loggerContext);
            fileAppender.setName("timestamp");
            // set the file name
            File logFile = new File(tradingWorkDir, id+".log");
            fileAppender.setFile(logFile.getAbsolutePath());

            PatternLayoutEncoder encoder = new PatternLayoutEncoder();
            encoder.setContext(loggerContext);
            encoder.setPattern("%d [%thread] %-5level %logger{35} - %msg %n");
            encoder.start();

            fileAppender.setEncoder(encoder);
            fileAppender.start();

            // attach the rolling file appender to the logger of your choice
            loggerCategory = AccountImpl.class.getName()+"."+id;
            ch.qos.logback.classic.Logger packageLogger = loggerContext.getLogger(loggerCategory);
            packageLogger.addAppender(fileAppender);
            packageLogger.setAdditive(true); //保证每个Account数据, 在主的日志中也有一份

            logger = loggerContext.getLogger(loggerCategory);
        } else {
            loggerCategory = AccountImpl.class.getName();
            logger = LoggerFactory.getLogger(AccountImpl.class);
        }
    }

    private AbsTxnSession createTxnSession(String provider) {
        TxnSessionFactory factory = tradeService.getTxnSessionFactories().get(provider);
        if ( factory!=null ) {
            return (AbsTxnSession)factory.create(beansContainer, this, this);
        }
        throw new RuntimeException("Unsupported account txn provider: "+provider);
    }

    PositionImpl getOrCreatePosition(Exchangeable e, boolean create) {
        PositionImpl pos = positions.get(e);
        if ( pos==null && create ) {
            pos = new PositionImpl(this, e);
            positions.put(e, pos);
        }
        return pos;
    }

    /**
     * 改变账户状态, 并通知AccountListener
     */
    private boolean changeState(AccountState newState) {
        boolean result = false;
        if ( newState!=state) {
            result = true;
            AccountState oldState = state;
            state = newState;
            logger.info("Account "+getId()+" in state "+newState);
            publishAccountStateChanged(oldState);
        }
        return result;
    }

    private void publishAccountStateChanged(AccountState oldState) {
        for(AccountListener listener:listeners) {
            try{
                listener.onAccountStateChanged(this, oldState);
            }catch(Throwable t) {
                logger.error("notify listener state change failed", t);
            }
        }
    }

    private void publishOrderStateChanged(Order order, OrderStateTuple lastStateTuple) {
        OrderListener odrListener = order.getListener();
        try{
            if ( odrListener!=null ) {
                odrListener.onOrderStateChanged(this, order, lastStateTuple);
            }
        }catch(Throwable t) {
            logger.error("notify listener "+odrListener+" order "+order.getRef()+" state change failed", t);
        }
        for(AccountListener listener:listeners) {
            try{
                listener.onOrderStateChanged(this, order, lastStateTuple);
            }catch(Throwable t) {
                logger.error("notify listener "+listener+" order "+order.getRef()+" state change failed", t);
            }
        }
        if ( null!=repository) {
            repository.asynSave(BOEntityType.Order, order.getId(), order);
        }
    }

    private void publishTransaction(Order order, Transaction txn) {
        OrderListener odrListener = order.getListener();
        try{
            if ( odrListener!=null ) {
                odrListener.onTransaction(this, order, txn);
            }
        }catch(Throwable t) {
            logger.error("notify listener "+odrListener+" order "+order.getRef()+" txn "+txn.getId()+" failed", t);
        }
        for(AccountListener listener:listeners) {
            try{
                listener.onTransaction(this, order, txn);
            }catch(Throwable t) {
                logger.error("notify listener "+listener+" on txn "+txn.getId(), t);
            }
        }
    }

    /**
     * 更新账户资金的持仓盈亏
     */
    private void updateAccountMoneyOnMarket() {
        long balance0 = getMoney(AccMoney.Balance);
        long avail0 = getMoney(AccMoney.Available);
        long frozenCommission=0;
        long commission=0;
        long frozenMargin=0;
        long margin=0;
        long posProfit =0;
        long posProfitToday=0;
        for(Position pos:positions.values()) {
            frozenCommission += pos.getMoney(PosMoney.FrozenCommission);
            commission += pos.getMoney(PosMoney.Commission);
            frozenMargin += pos.getMoney(PosMoney.FrozenMargin);
            margin += pos.getMoney(PosMoney.UseMargin);
            posProfit += pos.getMoney(PosMoney.PositionProfit);
            posProfitToday += pos.getMoney(PosMoney.PositionProfitToday);
        }
        long deposit = getMoney(AccMoney.Deposit);
        long withdraw = getMoney(AccMoney.Withdraw);
        long balanceBefore = getMoney(AccMoney.PreBalance);
        long balance = balanceBefore+getMoney(AccMoney.CloseProfit)-commission+posProfit+deposit-withdraw;
        long reserve = getMoney(AccMoney.Reserve);
        long avail = balance-margin-frozenMargin-frozenCommission-reserve;

        setMoney(AccMoney.Balance, balance);
        setMoney(AccMoney.PositionProfit, posProfit);
        setMoney(AccMoney.PositionProfitToday, posProfitToday);
        setMoney(AccMoney.Available, avail);
        setMoney(AccMoney.FrozenMargin, frozenMargin);
        setMoney(AccMoney.CurrMargin, margin);
        setMoney(AccMoney.FrozenCommission, frozenCommission);
        setMoney(AccMoney.Commission, commission);

    }

    /**
     * 本地冻结订单的保证金和手续费, 调整account/position的相关字段, 并保存数据到OrderImpl
     */
    private void localOrderFreeze(OrderImpl order) throws AppException {
        long orderFrozenMargin = order.getMoney(OdrMoney.LocalFrozenMargin);
        long orderFrozenCommission = order.getMoney(OdrMoney.LocalFrozenCommission);
        long frozenMargin0 = getMoney(AccMoney.FrozenMargin);
        long frozenCommission0 = getMoney(AccMoney.FrozenCommission);
        long avail0 = getMoney(AccMoney.Available);
        addMoney(AccMoney.FrozenMargin, orderFrozenMargin);
        addMoney(AccMoney.FrozenCommission, orderFrozenCommission);
        addMoney(AccMoney.Available, -1*(orderFrozenMargin+orderFrozenCommission));

        long frozenMargin2 = getMoney(AccMoney.FrozenMargin);
        long frozenCommission2 = getMoney(AccMoney.FrozenCommission);
        long avail2 = getMoney(AccMoney.Available);

        //验证资金冻结前后, (冻结+可用) 总额不变
        assert(frozenMargin0+frozenCommission0+avail0 == frozenMargin2+frozenCommission2+avail2);
    }

    /**
     * 本地订单解冻, 如果报单失败或取消或成交.
     * <BR>注意, 一个订单可能会多次调用
     */
    private long[] localOrderUnfreeze(OrderImpl order) {
        if ( order.getOffsetFlags()==OrderOffsetFlag.OPEN ) {
            assert(order.getMoney(OdrMoney.LocalUnfrozenMargin)!=0);
            assert(order.getMoney(OdrMoney.LocalFrozenMargin)!=0);
        }
        assert(order.getMoney(OdrMoney.LocalFrozenCommission)!=0);
        assert(order.getMoney(OdrMoney.LocalUnfrozenCommission)!=0);

        long marginToUnfreeze = order.getMoney(OdrMoney.LocalUnfrozenMargin) - order.getMoney(OdrMoney.LocalAccountUnfrozenMargin);
        long commissionToUnfreeze = order.getMoney(OdrMoney.LocalUnfrozenCommission) - order.getMoney(OdrMoney.LocalAccountUnfrozenCommission);
        addMoney(AccMoney.FrozenMargin, -1*marginToUnfreeze);
        addMoney(AccMoney.FrozenCommission, -1*commissionToUnfreeze);
        addMoney(AccMoney.Available, (marginToUnfreeze+commissionToUnfreeze));

        order.setMoney(OdrMoney.LocalAccountUnfrozenMargin, order.getMoney(OdrMoney.LocalUnfrozenMargin));
        order.setMoney(OdrMoney.LocalAccountUnfrozenCommission, order.getMoney(OdrMoney.LocalUnfrozenCommission));

        return new long[] {marginToUnfreeze, commissionToUnfreeze};
    }

    /**
     * 异步重新加载资金和持仓
     */
    private void asyncReload() {
        ExecutorService executorService = beansContainer.getBean(ExecutorService.class);
        executorService.execute(()->{
            try{
                //查询账户
                money = txnSession.syncQryAccounts();
                //查询持仓
                positions = loadPositions();
            }catch(Throwable t) {
                logger.error("Reload asset info failed", t);
            }
        });
    }

    private void incrementCancelCount(Exchangeable e) {
        AtomicInteger value = cancelCounts.get(e);
        if ( value==null ) {
            value = new AtomicInteger();
            cancelCounts.put(e, value);
        }
        value.incrementAndGet();
    }

}
