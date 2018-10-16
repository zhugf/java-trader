package trader.service.trade;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.core.FileAppender;
import trader.common.beans.BeansContainer;
import trader.common.config.ConfigUtil;
import trader.common.exception.AppException;
import trader.common.exchangeable.Exchangeable;
import trader.common.util.ConversionUtil;
import trader.common.util.JsonUtil;
import trader.common.util.PriceUtil;
import trader.common.util.StringUtil;
import trader.common.util.TraderHomeUtil;
import trader.service.ServiceConstants.AccountState;
import trader.service.ServiceErrorConstants;
import trader.service.md.MarketData;
import trader.service.md.MarketDataService;
import trader.service.trade.ctp.CtpTxnSession;

public class AccountImpl implements Account, TradeConstants, ServiceErrorConstants {

    private String id;
    private String loggerPackage;
    private Logger logger;
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

    public AccountImpl(TradeServiceImpl tradeService, BeansContainer beansContainer, Map elem) {
        this.tradeService = tradeService;
        id = ConversionUtil.toString(elem.get("id"));
        state = AccountState.Created;
        orderRefGen = new OrderRefGen(this, beansContainer);
        createAccountLogger();
        update(elem);

        TxnProvider provider = ConversionUtil.toEnum(TxnProvider.class, elem.get("txnProvider"));
        txnSession = createTxnSession(provider);
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public long getMoney(int moneyIdx) {
        return this.money[moneyIdx];
    }

    public long addMoney(int moneyIdx, long toAdd) {
        money[moneyIdx] += toAdd;
        return money[moneyIdx];
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
        long[] localOrderMoney = validateOrderReq(builder);
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
            order.setState(OrderState.Failed);
            throw t;
        }
    }

    @Override
    public JsonObject toJsonObject() {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("loggerPackage", loggerPackage);
        json.addProperty("state", state.name());
        json.add("txnSession", txnSession.toJsonObject());
        json.add("connectionProps", JsonUtil.object2json(connectionProps));
        JsonArray viewsArray = new JsonArray();
        for(AccountViewImpl view:views.values()) {
            viewsArray.add(view.toJsonObject());
        }
        json.add("views", viewsArray);
        return json;
    }

    @Override
    public String toString() {
        return toJsonObject().toString();
    }

    public boolean changeState(AccountState newState) {
        boolean result = false;
        if ( newState!=state) {
            result = true;
            AccountState oldState = state;
            state = newState;
            notifyStateChanged(oldState);
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

    public OrderRefGen getOrderRefGen() {
        return orderRefGen;
    }

    /**
     * 当报单状态发生变化时回调
     * @param account
     * @param order
     * @param lastState
     */
    void onOrderStateChanged(OrderImpl order, OrderState lastState) {

    }

    /**
     * 确认结算单, 加载账户持仓, 订单等信息
     */
    void initialize() {
        changeState(AccountState.Initialzing);
        long t0 = System.currentTimeMillis();
        try{
            //查询并确认结算单
            String settlement = txnSession.syncConfirmSettlement();
            if ( !StringUtil.isEmpty(settlement)) {
                logger.info("Account "+getId()+" settlement: \n"+settlement);
            }
            //加载品种的交易数据
            if ( null==feeEvaluator ) {
                feeEvaluator = txnSession.syncLoadFeeEvaluator();
                logger.info("Exchangeable fee infos: \n"+feeEvaluator.toJsonObject().toString());
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


    private void createAccountLogger() {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();

        FileAppender fileAppender = new FileAppender();
        fileAppender.setContext(loggerContext);
        fileAppender.setName("timestamp");
        // set the file name
        File logsDir = new File(TraderHomeUtil.getTraderDailyDir(), "logs");
        logsDir.mkdirs();
        File logFile = new File(logsDir, id+".log");
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
        packageLogger.setAdditive(false);

        logger = loggerContext.getLogger(loggerPackage+"."+AccountImpl.class.getSimpleName());
    }

    private AbsTxnSession createTxnSession(TxnProvider provider) {
        switch(provider) {
        case ctp:
            return new CtpTxnSession(tradeService, this);
        default:
            throw new RuntimeException("Unsupported account txn provider: "+provider);
        }
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

    private PositionImpl getOrCreatePosition(Exchangeable e, boolean create) {
        PositionImpl pos = positions.get(e);
        if ( pos==null && create ) {
            pos = new PositionImpl(e);
            positions.put(e, pos);
        }
        return pos;
    }

    private void notifyStateChanged(AccountState oldState) {
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
        long marginReq = order.getMoney(OdrMoney_LocalFrozenMargin);
        long commission = order.getMoney(OdrMoney_LocalFrozenCommission);
        long frozenMargin0 = getMoney(AccMoney_FrozenMargin);
        long frozenCommission0 = getMoney(AccMoney_FrozenCommission);
        long avail0 = getMoney(AccMoney_Available);
        addMoney(AccMoney_FrozenMargin, unit*marginReq);
        addMoney(AccMoney_FrozenCommission, unit*commission);
        addMoney(AccMoney_Available, -1*unit*(marginReq+commission));

        long frozenMargin2 = getMoney(AccMoney_FrozenMargin);
        long frozenCommission2 = getMoney(AccMoney_FrozenCommission);
        long avail2 = getMoney(AccMoney_Available);

        //验证资金冻结前后, (冻结+可用) 总额不变
        assert(frozenMargin0+frozenCommission0+avail0 == frozenMargin2+frozenCommission2+avail2);
    }

    private long[] validateOrderReq(OrderBuilder builder) throws AppException
    {
        validateOrderVolume(builder);
        return validateOrderMargin(builder);
    }

    /**
     * 检查报单请求, 看有无超出限制
     * @param builder
     */
    private void validateOrderVolume(OrderBuilder builder) throws AppException
    {
        AccountView view = builder.getView();
        Exchangeable e = builder.getExchangeable();
        Integer maxVolume = view.getMaxVolumes().get(e);
        if ( maxVolume==null ) {
            throw new AppException(ERRCODE_TRADE_EXCHANGEABLE_INVALID, "开单品种 "+e+" 不在视图 "+view.getId()+" 允许范围内");
        }
        int currVolume = 0;
        Position pos = getOrCreatePosition(e, false);
        if ( builder.getOffsetFlag()==OrderOffsetFlag.OPEN) {
            //检查仓位限制
            if ( pos!=null ) {
                switch(builder.getDirection()) {
                case Buy:
                    currVolume = pos.getVolume(PosVolume_LongPosition);
                    break;
                case Sell:
                    currVolume = pos.getVolume(PosVolume_ShortPosition);
                    break;
                }
            }
            if ( maxVolume!=null && maxVolume<(currVolume+builder.getVolume()) ) {
                throw new AppException(ERRCODE_TRADE_VOL_EXCEEDS_LIMIT, "开单超出视图 "+view.getId()+" 持仓数量限制 "+maxVolume+" : "+builder);
            }
        }else {
            //检查持仓限制
            if ( pos!=null ) {
                switch(builder.getDirection()) {
                case Buy:
                    currVolume = pos.getVolume(PosVolume_ShortPosition);
                    break;
                case Sell:
                    currVolume = pos.getVolume(PosVolume_LongPosition);
                    break;
                }
            }
            if ( currVolume<builder.getVolume() ) {
                throw new AppException(ERRCODE_TRADE_VOL_EXCEEDS_LIMIT, "平单超出账户 "+getId()+" 当前持仓数量 "+currVolume+" : "+builder);
            }
        }
    }

    /**
     * 校验报单的保证金
     */
    private long[] validateOrderMargin(OrderBuilder builder) throws AppException
    {
        long[] orderMoney = new long[OdrMoney_Count];
        AccountView view = builder.getView();
        Exchangeable e = builder.getExchangeable();
        long priceCandidate = getOrderPriceCandidate(builder);
        orderMoney[OdrMoney_PriceCandidate] = priceCandidate;
        long[] odrFees = feeEvaluator.compute(e, builder.getVolume(), priceCandidate, builder.getDirection(), builder.getOffsetFlag());
        long commission = odrFees[1];
        if ( builder.getOffsetFlag()==OrderOffsetFlag.OPEN) {
            //开仓, 检查是否有新的保证金需求
            long longMargin=0, shortMargin=0, longMargin2=0, shortMargin2=0;
            Position pos = getOrCreatePosition(e, false);
            if ( pos!=null ) {
                longMargin = pos.getMoney(PosMoney_LongUseMargin);
                shortMargin = pos.getMoney(PosMoney_ShortUseMargin);
                longMargin2 = longMargin;
                shortMargin2 = shortMargin;
            }
            if ( builder.getDirection()==OrderDirection.Buy) {
                longMargin2 += odrFees[0];
            } else {
                shortMargin2 += odrFees[0];
            }
            //计算新的保证金需求
            long posMargin = Math.max(longMargin, shortMargin);
            long posMargin2 = Math.max(longMargin2, shortMargin2);
            long orderMarginReq = posMargin2-posMargin;
            long avail = getMoney(AccMoney_Available);
            if( avail <= orderMarginReq+commission ) {
                throw new AppException(ERRCODE_TRADE_MARGIN_NOT_ENOUGH, "账户 "+getId()+" 可用保证金 "+PriceUtil.long2price(avail)+" 不足");
            }
            money[OdrMoney_LocalFrozenMargin] = orderMarginReq;
        }else {
            //平仓, 解冻保证金这里没法计算
        }
        money[OdrMoney_LocalFrozenCommission] = commission;

        return orderMoney;
    }

    /**
     * 返回订单的保证金冻结用的价格, 市价使用最高/最低价格
     */
    long getOrderPriceCandidate(OrderBuilder builder) {
        MarketDataService mdService = tradeService.getBeansContainer().getBean(MarketDataService.class);
        MarketData md = mdService.getLastData(builder.getExchangeable());
        switch(builder.getPriceType()) {
        case Unknown:
        case AnyPrice:
            if ( builder.getDirection()==OrderDirection.Buy ) {
                return md.highestPrice;
            }else {
                return md.lowestPrice;
            }
        case BestPrice:
            return md.lastPrice;
        case LimitPrice:
            return builder.getLimitPrice();
        }
        return md.lastPrice;
    }

}
