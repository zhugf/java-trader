package trader.service.trade;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lmax.disruptor.BatchEventProcessor;
import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.BusySpinWaitStrategy;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.SleepingWaitStrategy;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.util.DaemonThreadFactory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.core.FileAppender;
import trader.common.beans.BeansContainer;
import trader.common.beans.Lifecycle;
import trader.common.config.ConfigUtil;
import trader.common.event.AsyncEvent;
import trader.common.event.AsyncEventFactory;
import trader.common.exception.AppException;
import trader.common.exchangeable.Exchange;
import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.MarketDayUtil;
import trader.common.util.ConversionUtil;
import trader.common.util.DateUtil;
import trader.common.util.FileUtil;
import trader.common.util.JsonUtil;
import trader.common.util.PriceUtil;
import trader.common.util.StringUtil;
import trader.common.util.TraderHomeUtil;
import trader.service.ServiceConstants.AccountState;
import trader.service.ServiceErrorConstants;
import trader.service.data.KVStore;
import trader.service.data.KVStoreService;
import trader.service.md.MarketData;
import trader.service.md.MarketDataService;

/**
 * 一个交易账户和通道实例对象.
 * <BR>每个Account对象实例有自己的RingBuffer, 有独立的Log文件, 有独立的多线程处理策略.
 * <BR>每个交易策略实例是运行在独立的线程中, 使用disruptor作为独立的调度
 */
public class AccountImpl implements Account, Lifecycle, EventHandler<AsyncEvent>, TradeConstants, ServiceErrorConstants {

    private String id;
    private String loggerPackage;
    private Logger logger;
    private File tradingWorkDir;
    private KVStore kvStore;
    private long[] money = new long[AccMoney_Count];
    private AccountState state;
    private TradeServiceImpl tradeService;
    private AbsTxnSession txnSession;
    private TxnFeeEvaluator feeEvaluator;
    private OrderRefGen orderRefGen;
    private Properties connectionProps;
    private List<AccountListener> listeners = new ArrayList<>();
    private Map<Exchangeable, PositionImpl> positions = new HashMap<>();
    private Map<String, AccountViewImpl> views = new LinkedHashMap<>();
    private Map<String, OrderImpl> orders = new ConcurrentHashMap<>();
    private Disruptor<AsyncEvent> disruptor;
    private RingBuffer<AsyncEvent> ringBuffer;
    private BeansContainer beansContainer;
    private BatchEventProcessor<AsyncEvent> ringProcessor;

    public AccountImpl(TradeServiceImpl tradeService, BeansContainer beansContainer, Map elem) {
        this.tradeService = tradeService;
        id = ConversionUtil.toString(elem.get("id"));
        state = AccountState.Created;
        String provider = ConversionUtil.toString(elem.get("provider"));

        LocalDate tradingDay = detectTradingDay(provider);
        tradingWorkDir = new File(TraderHomeUtil.getDirectory(TraderHomeUtil.DIR_WORK), DateUtil.date2str(tradingDay));
        createAccountLogger();

        try{
            kvStore = beansContainer.getBean(KVStoreService.class).getStore(id);
        }catch(Throwable t) {
            logger.error("Create datastore failed", t);
        }
        orderRefGen = new OrderRefGen(this, beansContainer);

        update(elem);

        txnSession = createTxnSession(provider);
        createDiruptor();
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
    public Position getPosition(Exchangeable e) {
        return positions.get(e);
    }

    @Override
    public Collection<? extends Position> getPositions(AccountView view){
        Collection<? extends Position> result = null;
        if ( view==null ) {
            result = positions.values();
        } else {
            var viewPos = new ArrayList<PositionImpl>();
            for(PositionImpl pos:positions.values()) {
                if ( ((AccountViewImpl)view).accept(pos.getExchangeable())) {
                    viewPos.add(pos);
                }
            }
            result = viewPos;
        }
        return result;
    }

    @Override
    public Map<String, ? extends AccountView> getViews() {
        return views;
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
    public synchronized Order createOrder(OrderBuilder builder) throws AppException {
        long[] localOrderMoney = (new OrderValidator(this, builder)).validate();
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
     * 确认结算单, 加载账户持仓, 订单等信息
     */
    @Override
    public void init(BeansContainer beansContainer) {
        this.beansContainer = beansContainer;
        changeState(AccountState.Initialzing);
        ringBuffer = disruptor.start();

        ringProcessor = new BatchEventProcessor<AsyncEvent>(ringBuffer, ringBuffer.newBarrier(), this);
        ringBuffer.addGatingSequences(ringProcessor.getSequence());

        long t0 = System.currentTimeMillis();
        try{
            //查询并确认结算单
            String settlement = txnSession.syncConfirmSettlement();
            if ( !StringUtil.isEmpty(settlement)) {
                logger.info("Account "+getId()+" settlement: \n"+settlement);
            }
            //加载品种的交易数据
            if ( null==feeEvaluator ) {
                loadFeeEvaluator(beansContainer);
            }
            for(AccountViewImpl view:views.values()) {
                view.resolveExchangeables();
            }
            //查询账户
            money = txnSession.syncQryAccounts();
            //查询持仓
            positions = new HashMap<>();
            for(PositionImpl pos:txnSession.syncQryPositions()) {
                positions.put(pos.getExchangeable(), pos);
            }
            //分配持仓到View
            for(PositionImpl p:positions.values()) {
                assignPositionView(p);
            }
            long t1 = System.currentTimeMillis();
            changeState(AccountState.Ready);
            logger.info("Account "+getId()+" initialize in "+(t1-t0)+" ms");
        }catch(Throwable t) {
            logger.error("Account "+getId()+" initialize failed", t);
            changeState(AccountState.NotReady);
        }
    }

    @Override
    public void destroy() {
        if ( ringBuffer!=null ) {
            ringBuffer.removeGatingSequence(ringProcessor.getSequence());
            disruptor.shutdown();
            ringBuffer = null;
            ringProcessor = null;
        }
    }

    /**
     * 更新配置属性
     * @return true 如果有变化, false 如果相同
     */
    public boolean update(Map elem) {
        boolean result = false;
        Properties connectionProps2 = StringUtil.text2properties((String)elem.get("text"));
        if ( !connectionProps2.equals(connectionProps) ) {
            this.connectionProps = connectionProps2;
            result = true;
        }
        result |= updateViews();
        return result;
    }

    public boolean changeState(AccountState newState) {
        boolean result = false;
        if ( newState!=state) {
            result = true;
            AccountState oldState = state;
            state = newState;
            publishStateChanged(oldState);
        }
        return result;
    }

    public void setViews(Map<String, AccountViewImpl> views0) {
        views = views0;
    }

    public void setPositions(Map<Exchangeable, PositionImpl> positions) {

    }

    public AccountView viewAccept(Exchangeable e) {
        for(AccountViewImpl v:views.values()) {
            if ( v.accept(e)) {
                return v;
            }
        }
        return null;
    }

    public String getLoggerPackage() {
        return loggerPackage;
    }

    public RingBuffer<AsyncEvent> getRingBuffer(){
        return ringBuffer;
    }

    public OrderRefGen getOrderRefGen() {
        return orderRefGen;
    }

    public BeansContainer getBeansContainer() {
        return beansContainer;
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("loggerPackage", loggerPackage);
        json.addProperty("state", state.name());
        json.add("txnSession", txnSession.toJson());
        json.add("connectionProps", JsonUtil.object2json(connectionProps));
        JsonArray viewsArray = new JsonArray();
        for(AccountViewImpl view:views.values()) {
            viewsArray.add(view.toJson());
        }
        json.add("views", viewsArray);
        return json;
    }

    @Override
    public String toString() {
        return toJson().toString();
    }

    /**
     * 处理从CtpTxnSession过来的事件, 和MarketData事件
     */
    @Override
    public void onEvent(AsyncEvent event, long sequence, boolean endOfBatch) throws Exception {
        switch(event.eventType) {
        case AsyncEvent.EVENT_TYPE_PROCESSOR:
            event.processor.process(event.dataType, event.data, event.data2);
            break;
        case AsyncEvent.EVENT_TYPE_MARKETDATA:
            //TODO 更新Account当前持仓和可用资金
            break;
        }
    }

    /**
     * 当市场价格发生变化, 更新持仓盈亏
     */
    void onMarketData(MarketData marketData) {
        PositionImpl pos = positions.get(marketData.instrumentId);
        if( pos!=null && pos.getVolume(PosVolume_Position)>0 ) {
            pos.onMarketData(marketData);
        }
    }

    /**
     * 当报单状态发生变化时回调
     * @param account
     * @param order
     * @param lastState
     */
    void onOrderStateChanged(OrderImpl order, OrderStateTuple oldState) {
        PositionImpl pos = ((PositionImpl)order.getPosition());
        OrderStateTuple lastState = order.getState();
        switch(lastState.getState()) {
        case Failed:
            //报单失败, 本地回退冻结仓位和资金
            synchronized(this) {
                localUnfreeze(order);
                pos.localUnfreeze(order);
            }
            break;
        }
    }

    /**
     * 处理成交回报, 更新本地仓位和资金数据
     */
    void onTransaction(OrderImpl order, Transaction txn, long timestamp) {
        long[] lastOrderMoney = order.getMoney();
        long odrUnfrozenCommision0 = order.getMoney(OdrMoney_LocalUnfrozenCommission);
        long odrUsedCommission0 = order.getMoney(OdrMoney_LocalUsedCommission);
        long[] txnFees = feeEvaluator.compute(txn);
        if ( !order.attachTransaction(txn, txnFees, timestamp) ) {
            if( logger.isErrorEnabled() ) {
                logger.error("报单 "+order.getRef()+" 拒绝成交事件: "+txn.getId()+" "+txn.getDirection()+" 价 "+PriceUtil.long2price(txn.getPrice())+" 量 "+txn.getVolume());
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
    }

    private void loadFeeEvaluator(BeansContainer beansContainer) throws Exception
    {
        Collection<Exchangeable> subscriptions = Collections.emptyList();
        MarketDataService mdService = beansContainer.getBean(MarketDataService.class);
        if ( mdService!=null ) {
            subscriptions = mdService.getSubscriptions();
        }
        File commissionsJson = new File(tradingWorkDir, id+".commissions.json");
        if ( commissionsJson.exists() ) {
            feeEvaluator = FutureFeeEvaluator.fromJson((JsonObject)(new JsonParser()).parse(FileUtil.read(commissionsJson)));
            logger.info("加载品种保证金手续费信息: "+new TreeSet<>(feeEvaluator.getExchangeables()));
        }else {
            long t0 = System.currentTimeMillis();
            feeEvaluator = txnSession.syncLoadFeeEvaluator(subscriptions);
            long t1 = System.currentTimeMillis();
            logger.info("查询品种保证金手续费信息, 耗时 "+(t1-t0)+" ms");
            FileUtil.save(commissionsJson, feeEvaluator.toJson().toString());
        }
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
            return (AbsTxnSession)factory.create(tradeService, this);
        }
        throw new RuntimeException("Unsupported account txn provider: "+provider);
    }

    private void createDiruptor() {
        String waitStrategyPath = TradeServiceImpl.ITEM_ACCOUNT+"#"+getId()+"/waitStrategy";
        String waitStrategyCfg = ConfigUtil.getString(waitStrategyPath);
        WaitStrategy waitStrategy = null;
        if ( "BusySpin".equalsIgnoreCase(waitStrategyCfg) ) {
            waitStrategy = new BusySpinWaitStrategy();
        }else if ("Sleeping".equalsIgnoreCase(waitStrategyCfg) ){
            waitStrategy = new SleepingWaitStrategy();
        }else {
            waitStrategy = new BlockingWaitStrategy();
        }
        disruptor = new Disruptor<AsyncEvent>(new AsyncEventFactory(), 65536, DaemonThreadFactory.INSTANCE,ProducerType.MULTI, waitStrategy);
    }

    private boolean updateViews() {
        String path = TradeServiceImpl.ITEM_ACCOUNT+"#"+getId()+"/view[]";
        Map<String, AccountViewImpl> currViews = new HashMap<>((Map)views);
        Map<String, AccountViewImpl> allViews = new LinkedHashMap<>();
        var newViewIds = new ArrayList<String>();

        var viewElems = (List<Map>)ConfigUtil.getObject(path);
        for(Map viewElem: viewElems) {
            String id = ConversionUtil.toString(viewElem.get("id"));
            AccountViewImpl view = currViews.get(id);
            if ( view==null ) {
                view = new AccountViewImpl(this, viewElem);
                newViewIds.add(id);
            }else {
                view.update(viewElem);
            }
            allViews.put(id, view);
        }
        this.views = (allViews);
        String message = "Account "+getId()+" load "+allViews.size()+" views, new/updated: "+newViewIds;
        if ( newViewIds.size()>0 ) {
            logger.info(message);
        } else {
            if ( logger.isDebugEnabled() ) {
                logger.debug(message);
            }
        }
        return newViewIds.size()>0;
    }

    private AccountViewImpl assignPositionView(PositionImpl p) {
        for(AccountViewImpl view:views.values()) {
            if ( view.accept(p) ) {
                return view;
            }
        }
        return null;
    }

    PositionImpl getOrCreatePosition(Exchangeable e, boolean create) {
        PositionImpl pos = positions.get(e);
        if ( pos==null && create ) {
            pos = new PositionImpl(e);
            positions.put(e, pos);
        }
        return pos;
    }

    private void publishStateChanged(AccountState oldState) {
        for(AccountListener listener:listeners) {
            try{
                listener.onAccountStateChanged(this, oldState);
            }catch(Throwable t) {
                logger.error("notify listener state change failed", t);
            }
        }
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

    /**
     * 探测交易日
     * @return
     */
    private LocalDate detectTradingDay(String provider) {
        LocalDate result = LocalDate.now();
        switch(provider) {
        case TxnSession.PROVIDER_CTP:
        {
            LocalDate date = LocalDate.now();
            LocalDateTime dateTime = LocalDateTime.now();
            boolean isMarketDay = MarketDayUtil.isMarketDay(Exchange.SHFE, date);
            if ( dateTime.getHour()<=15 ) {
                if ( isMarketDay ) {
                    result = date;
                } else {
                    result = null;
                }
            } else if ( dateTime.getHour()>=17 ) {
                result = MarketDayUtil.nextMarketDay(Exchange.SHFE, date);
            } else if ( dateTime.getHour()<=3 ) {
                result = MarketDayUtil.nextMarketDay(Exchange.SHFE, date.plusDays(-1));
            }
        }
        }
        return result;
    }

}
