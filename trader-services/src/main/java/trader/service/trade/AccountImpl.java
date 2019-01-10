package trader.service.trade;

import java.io.File;
import java.io.StringReader;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.core.FileAppender;
import trader.common.beans.BeansContainer;
import trader.common.exception.AppException;
import trader.common.exchangeable.Exchange;
import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.MarketDayUtil;
import trader.common.util.ConversionUtil;
import trader.common.util.DateUtil;
import trader.common.util.FileUtil;
import trader.common.util.IniFile;
import trader.common.util.JsonUtil;
import trader.common.util.PriceUtil;
import trader.common.util.StringUtil;
import trader.common.util.TraderHomeUtil;
import trader.service.ServiceConstants.AccountState;
import trader.service.ServiceConstants.ConnState;
import trader.service.ServiceErrorConstants;
import trader.service.data.KVStore;
import trader.service.data.KVStoreService;
import trader.service.md.MarketData;
import trader.service.md.MarketDataService;
import trader.service.trade.spi.AbsTxnSession;
import trader.service.trade.spi.TxnSessionListener;

/**
 * 一个交易账户和通道实例对象.
 * <BR>每个Account对象实例有自己的RingBuffer, 有独立的Log文件, 有独立的多线程处理策略.
 * <BR>每个交易策略实例是运行在独立的线程中, 使用disruptor作为独立的调度
 */
public class AccountImpl implements Account, TxnSessionListener, TradeConstants, ServiceErrorConstants {

    private String id;
    private String loggerPackage;
    private Logger logger;
    private File tradingWorkDir;
    private KVStore kvStore;
    private long[] money = new long[AccMoney_Count];
    private AccountState state;
    private TradeService tradeService;
    private AbsTxnSession txnSession;
    private TxnFeeEvaluator feeEvaluator;
    private OrderRefGen orderRefGen;
    private Properties connectionProps;
    /**
     * 配置的期货公司的保证金调整
     */
    private Properties brokerMarginRatio = new Properties();
    private List<AccountListener> listeners = new ArrayList<>();
    private Map<Exchangeable, PositionImpl> positions = new HashMap<>();
    private Map<String, OrderImpl> orders = new ConcurrentHashMap<>();
    private BeansContainer beansContainer;

    public AccountImpl(TradeService tradeService, BeansContainer beansContainer, Map configElem) {
        this.tradeService = tradeService;
        this.beansContainer = beansContainer;
        id = ConversionUtil.toString(configElem.get("id"));
        state = AccountState.Created;
        String provider = ConversionUtil.toString(configElem.get("provider"));

        LocalDate tradingDay = MarketDayUtil.getTradingDay(Exchange.SHFE, LocalDateTime.now());
        tradingWorkDir = new File(TraderHomeUtil.getDirectory(TraderHomeUtil.DIR_WORK), DateUtil.date2str(tradingDay));
        createAccountLogger();

        try{
            kvStore = beansContainer.getBean(KVStoreService.class).getStore("account."+id+".");
        }catch(Throwable t) {
            logger.error("Create datastore failed", t);
        }
        orderRefGen = new OrderRefGen(this, beansContainer);
        update(configElem);
        txnSession = createTxnSession(provider);
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public KVStore getStore() {
        return kvStore;
    }

    @Override
    public long getMoney(int moneyIdx) {
        return this.money[moneyIdx];
    }

    public long addMoney(int moneyIdx, long toAdd) {
        money[moneyIdx] += toAdd;
        return money[moneyIdx];
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
    public AccountState getState() {
        return state;
    }

    @Override
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
    public Collection<? extends Order> getOrders() {
        return orders.values();
    }

    @Override
    public Order getOrder(String orderRef) {
        return orders.get(orderRef);
    }

    @Override
    public Collection<? extends Position> getPositions() {
        return new ArrayList<>(positions.values());
    }

    @Override
    public Position getPosition(Exchangeable e) {
        return positions.get(e);
    }

    @Override
    public void addAccountListener(AccountListener listener) {
        if ( listener!=null && !listeners.contains(listener)) {
            var v = new ArrayList<>(listeners);
            v.add(listener);
            listeners = v;
        }
    }

    @Override
    public void removeAccountListener(AccountListener listener) {
        if ( listener!=null && listeners.contains(listener)) {
            var v = new ArrayList<>(listeners);
            v.remove(listener);
            listeners = v;
        }
    }

    @Override
    public synchronized Order createOrder(OrderBuilder builder) throws AppException {
        long[] localOrderMoney = (new OrderValidator(beansContainer, this, builder)).validate();
        //创建Order
        Exchangeable e = builder.getExchangeable();
        OrderImpl order = new OrderImpl(e, orderRefGen.nextRefId(),
            builder.getPriceType(), builder.getOffsetFlag(), builder.getLimitPrice(), builder.getVolume(), builder.getVolumeCondition());
        PositionImpl pos = null;
        try {
            orders.put(order.getRef(), order);
            //关联Position
            pos = getOrCreatePosition(e, true);
            //本地计算和冻结仓位和保证金
            order.setMoney(OdrMoney_LocalFrozenMargin, localOrderMoney[OdrMoney_LocalFrozenMargin]);
            order.setMoney(OdrMoney_LocalFrozenCommission, localOrderMoney[OdrMoney_LocalFrozenCommission]);
            localFreeze(order);
            //仓位管理
            pos.localFreeze(order);
            order.attachPosition(pos);
            //异步发送
            txnSession.asyncSendOrder(order);
            return order;
        }catch(AppException t) {
            logger.error("报单错误", t);
            //回退本地已冻结资金和仓位
            localUnfreeze(order);
            pos.localUnfreeze(order);
            if ( order.getState()==OrderStateTuple.STATE_UNKNOWN ) {
                order.changeState(new OrderStateTuple(OrderState.Failed, OrderSubmitState.Unsubmitted, System.currentTimeMillis(), t.toString()));
            }
            throw t;
        }
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

        Properties brokerMarginRatio2 = configIni.getSection("brokerMarginRatio").getProperties();
        if ( !brokerMarginRatio.equals(brokerMarginRatio2)) {
            this.brokerMarginRatio = brokerMarginRatio2;
            result = true;
        }

        Properties connectionProps2 = configIni.getSection("connectionProps").getProperties();
        if ( !connectionProps2.equals(connectionProps) ) {
            this.connectionProps = connectionProps2;
            result = true;
        }
        return result;
    }

    @Override
    public String getLoggerPackage() {
        return loggerPackage;
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("loggerPackage", loggerPackage);
        json.addProperty("state", state.name());
        json.add("txnSession", txnSession.toJson());
        json.add("connectionProps", JsonUtil.object2json(connectionProps));
        json.add("brokerMarginRatio", JsonUtil.object2json(brokerMarginRatio));
        json.add("money", TradeConstants.accMoney2json(money));
        return json;
    }

    @Override
    public String toString() {
        return toJson().toString();
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
    public void createTransaction(String txnId, String orderRef, OrderDirection txnDirection, OrderOffsetFlag txnFlag, long txnPrice, int txnVolume, long txnTime, Object txnData) {
        OrderImpl order = (OrderImpl)getOrder(orderRef);
        if ( order ==null ){
            logger.error("Account "+getId()+" order is not found "+orderRef+" with txn id: "+txnId);
        }
        TransactionImpl txn = new TransactionImpl(
                orderRef,
                order,
                txnDirection,
                txnFlag,
                txnPrice,
                txnVolume,
                txnTime
                );
        onTransaction(order, txn, System.currentTimeMillis());
    }

    /**
     * 当报单状态发生变化时回调
     */
    @Override
    public OrderStateTuple changeOrderState(String orderRef, OrderStateTuple newState, Map<String,String> attrs) {
        OrderStateTuple oldState = null;
        Order order = orders.get(orderRef);
        if ( order==null ) {
            logger.info("Account "+getId()+" order is not found: "+orderRef);
        } else {
            oldState = changeOrderState(order, newState, attrs);
        }
        return oldState;
    }

    /**
     * 当报单状态发生变化时回调
     */
    @Override
    public OrderStateTuple changeOrderState(Order order0, OrderStateTuple newState, Map<String, String> attrs)
    {
        OrderImpl order = (OrderImpl)order0;
        if ( attrs!=null ) {
            for(Map.Entry<String, String> attrEntry:attrs.entrySet()) {
                order.setAttr(attrEntry.getKey(), attrEntry.getValue());
            }
        }
        OrderStateTuple oldState = order.changeState(newState);
        if ( oldState!=null ) {
            logger.info("Account "+getId()+" order "+order.getRef()+" changed state to "+newState+", old state: "+oldState);
            PositionImpl pos = ((PositionImpl)order.getPosition());
            switch(newState.getState()) {
            case Failed: //报单失败, 本地回退冻结仓位和资金
            case Canceled: //报单取消, 本地回退冻结仓位和资金
            case PartiallyDeleted: //部分取消, 本地回退取消部分的冻结仓位和资金
                synchronized(this) {
                    localUnfreeze(order);
                    pos.localUnfreeze(order);
                }
                break;
            default:
                break;
            }
            publishOrderStateChanged(order, oldState);
        } else {
            logger.warn("Account "+getId()+" order "+order.getRef()+" is FAILED to change state from "+order.getState()+" to "+newState);
        }
        return oldState;
    }

    @Override
    public void compareAndSetRef(String orderRef) {
        orderRefGen.compareAndSetRef(orderRef);
    }

    /**
     * 当市场价格发生变化, 更新持仓盈亏
     */
    public void onMarketData(MarketData marketData) {
        if ( state!=AccountState.Ready ) {
            return;
        }
        boolean priceChanged = false;
        PositionImpl pos = positions.get(marketData.instrumentId);
        if( pos!=null && pos.getVolume(PosVolume_Position)>0 ) {
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
        long[] lastOrderMoney = order.getMoney();
        long odrUnfrozenCommision0 = order.getMoney(OdrMoney_LocalUnfrozenCommission);
        long odrUsedCommission0 = order.getMoney(OdrMoney_LocalUsedCommission);
        long[] txnFees = feeEvaluator.compute(txn);
        if ( !order.attachTransaction(txn, txnFees, timestamp) ) {
            if( logger.isErrorEnabled() ) {
                logger.error("Account "+getId()+" order "+order.getRef()+" refuse transaction event: "+txn.getId()+" "+txn.getDirection()+" price "+PriceUtil.long2price(txn.getPrice())+" vol "+txn.getVolume());
            }
            return;
        }
        long odrUsedCommission2 = order.getMoney(OdrMoney_LocalUsedCommission)-odrUsedCommission0;
        long odrUnfrozenCommission2 = order.getMoney(OdrMoney_LocalUnfrozenCommission);

        //解冻手续费
        if( odrUnfrozenCommission2!=odrUnfrozenCommision0 ) {
            long txnUnfrozenCommission = Math.abs(odrUnfrozenCommission2-odrUnfrozenCommision0);
            transferMoney(AccMoney_FrozenCommission, AccMoney_Available, txnUnfrozenCommission );
        }
        //更新实际手续费
        if( odrUsedCommission2!=odrUsedCommission0) {
            long txnUsedCommission = Math.abs(odrUsedCommission2-odrUsedCommission0);
            transferMoney(AccMoney_Available, AccMoney_Commission, txnUsedCommission);
            addMoney(AccMoney_Balance, -1*txnUsedCommission);
        }
        //更新账户保证金占用等等
        PositionImpl position = ((PositionImpl)order.getPosition());
        long closeProfit0 = position.getMoney(PosMoney_CloseProfit);
        //更新持仓和资金
        position.onTransaction(order, txn, txnFees, lastOrderMoney);
        long txnProfit2 = position.getMoney(PosMoney_CloseProfit)-closeProfit0;
        //更新平仓利润
        if ( txnProfit2!=0 ) {
            addMoney(AccMoney_CloseProfit, txnProfit2);
        }
        //更新
        publishTransaction(txn);
    }

    private void loadFeeEvaluator() throws Exception
    {
        Collection<Exchangeable> subscriptions = Collections.emptyList();
        MarketDataService mdService = beansContainer.getBean(MarketDataService.class);
        if ( mdService!=null ) {
            subscriptions = mdService.getSubscriptions();
        }
        File commissionsJson = new File(tradingWorkDir, id+".commissions.json");
        if ( commissionsJson.exists() ) {
            feeEvaluator = FutureFeeEvaluator.fromJson(brokerMarginRatio, (JsonObject)(new JsonParser()).parse(FileUtil.read(commissionsJson)));
            logger.info("Load fee info: "+new TreeSet<>(feeEvaluator.getExchangeables()));
        }else {
            String commissionsExchange = txnSession.syncLoadFeeEvaluator(subscriptions);
            File commissionsExchangeJson = new File(tradingWorkDir, id+".commissions-exchange.json");
            FileUtil.save(commissionsExchangeJson, commissionsExchange);
            FutureFeeEvaluator feeEvaluator = FutureFeeEvaluator.fromJson(brokerMarginRatio, (JsonObject)(new JsonParser()).parse(commissionsExchange));
            this.feeEvaluator = feeEvaluator;
            this.brokerMarginRatio = feeEvaluator.getBrokerMarginRatio();
            FileUtil.save(commissionsJson, feeEvaluator.toJson().toString());
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
            List<PositionDetailImpl> details = new ArrayList<>(detailArray.size()+1);
            for(int i=0;i<detailArray.size();i++) {
                JsonObject detailInfo = (JsonObject)detailArray.get(i);
                details.add(new PositionDetailImpl(
                    ConversionUtil.toEnum(PosDirection.class, detailInfo.get("direction").getAsString()),
                    detailInfo.get("volume").getAsInt(),
                    PriceUtil.str2long(detailInfo.get("price").getAsString()),
                    DateUtil.str2localdate(detailInfo.get("openDate").getAsString()).atStartOfDay(),
                    detailInfo.get("today").getAsBoolean()
                ));
            }
            PositionImpl pos = new PositionImpl(this, e, direction, money, volumes, details);
            positions.put(e, pos);
        }
        return positions;
    }

    /**
     * 从文件或实时查询保证金手续费信息
     */
    private void createAccountLogger() {
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
        loggerPackage = AccountImpl.class.getPackageName()+".account."+id;
        Logger packageLogger = loggerContext.getLogger(loggerPackage);
        packageLogger.addAppender(fileAppender);
        packageLogger.setAdditive(true); //保证每个Account数据, 在主的日志中也有一份

        logger = loggerContext.getLogger(loggerPackage+"."+AccountImpl.class.getSimpleName());
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
            pos = new PositionImpl(e);
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
        for(AccountListener listener:listeners) {
            try{
                listener.onOrderStateChanged(this, order, lastStateTuple);
            }catch(Throwable t) {
                logger.error("notify listener state change failed", t);
            }
        }
    }

    private void publishTransaction(Transaction txn) {
        for(AccountListener listener:listeners) {
            try{
                listener.onTransaction(this, txn);
            }catch(Throwable t) {
                logger.error("notify listener state change failed", t);
            }
        }
    }

    /**
     * 更新账户资金的持仓盈亏
     */
    private void updateAccountMoneyOnMarket() {
        long frozenCommission=0;
        long commission=0;
        long frozenMargin=0;
        long margin=0;
        long posProfit =0;
        for(Position pos:positions.values()) {
            frozenCommission += pos.getMoney(PosMoney_FrozenCommission);
            commission += pos.getMoney(PosMoney_Commission);
            frozenMargin += pos.getMoney(PosMoney_FrozenMargin);
            margin += pos.getMoney(PosMoney_UseMargin);
            posProfit += pos.getMoney(PosMoney_PositionProfit);
        }
        long balanceBefore = money[AccMoney_BalanceBefore];
        long reserve = money[AccMoney_Reserve];
        long avail = balanceBefore-frozenMargin-margin-commission-frozenCommission-reserve+posProfit;

        money[AccMoney_Balance] = balanceBefore+posProfit-commission;
        money[AccMoney_PositionProfit] = posProfit;
        money[AccMoney_Available] = avail;
        money[AccMoney_FrozenMargin] = frozenMargin;
        money[AccMoney_CurrMargin] = margin;
        money[AccMoney_FrozenCommission] = frozenCommission;
        money[AccMoney_Commission] = commission;
    }

    /**
     * 本地冻结订单的保证金和手续费, 调整account/position的相关字段, 并保存数据到OrderImpl
     */
    private void localFreeze(OrderImpl order) throws AppException {
        assert(order.getMoney(AccMoney_FrozenMargin)!=0);
        assert(order.getMoney(AccMoney_FrozenCommission)!=0);
        localFreeze0(order, 1);
    }

    /**
     * 本地订单解冻, 如果报单失败
     */
    private void localUnfreeze(OrderImpl order) {
        localFreeze0(order, -1);
        order.addMoney(OdrMoney_LocalUnfrozenMargin, order.getMoney(OdrMoney_LocalFrozenMargin) );
        order.addMoney(OdrMoney_LocalUnfrozenCommission, order.getMoney(OdrMoney_LocalFrozenCommission) );
    }

    /**
     * 本地冻结或解冻订单相关资金
     */
    private void localFreeze0(OrderImpl order, int unit) {
        long orderFrozenMargin = order.getMoney(OdrMoney_LocalFrozenMargin) - order.getMoney(OdrMoney_LocalUnfrozenMargin);
        long orderFrozenCommission = order.getMoney(OdrMoney_LocalFrozenCommission) - order.getMoney(OdrMoney_LocalUnfrozenCommission);
        long frozenMargin0 = getMoney(AccMoney_FrozenMargin);
        long frozenCommission0 = getMoney(AccMoney_FrozenCommission);
        long avail0 = getMoney(AccMoney_Available);
        addMoney(AccMoney_FrozenMargin, unit*orderFrozenMargin);
        addMoney(AccMoney_FrozenCommission, unit*orderFrozenCommission);
        addMoney(AccMoney_Available, -1*unit*(orderFrozenMargin+orderFrozenCommission));

        long frozenMargin2 = getMoney(AccMoney_FrozenMargin);
        long frozenCommission2 = getMoney(AccMoney_FrozenCommission);
        long avail2 = getMoney(AccMoney_Available);

        //验证资金冻结前后, (冻结+可用) 总额不变
        assert(frozenMargin0+frozenCommission0+avail0 == frozenMargin2+frozenCommission2+avail2);
    }

}
