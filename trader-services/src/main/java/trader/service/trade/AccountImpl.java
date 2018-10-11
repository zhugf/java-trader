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
    private long[] money = new long[AccountMoney_Count];
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
    public Collection<? extends Position> getPositions(AccountView view){
        return positions.values();
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
    public Order createOrder(OrderBuilder builder) throws AppException {
        validateOrderReq(builder);
        return null;
    }

    @Override
    public JsonObject toJsonObject() {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("loggerPackage", loggerPackage);
        json.addProperty("state", state.name());
        json.add("txnSession", txnSession.toJsonObject());
        json.add("connectionProps", JsonUtil.props2json(connectionProps));
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
     * 检查报单请求, 看有无超出限制
     * @param builder
     */
    private void validateOrderReq(OrderBuilder builder) throws AppException
    {
        AccountView view = builder.getView();
        Exchangeable e = builder.getExchangeable();
        Integer maxVolume = view.getMaxVolumes().get(e);
        if ( maxVolume==null ) {
            throw new AppException(ERRCODE_TRADE_EXCHANGEABLE_INVALID, "开单品种 "+e+" 不在视图 "+view.getId()+" 允许范围内");
        }
        long priceCandidate = getOrderPriceCandidate(builder);
        int currVolume = 0;
        Position pos = positions.get(e);
        switch(builder.getOffsetFlag()) {
        case OPEN:
        {
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
        }
        case CLOSE:
        case CLOSE_TODAY:
        case CLOSE_YESTERDAY:
        {
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
            //TODO 平仓需要检查今仓昨仓
            if ( currVolume<builder.getVolume() ) {
                throw new AppException(ERRCODE_TRADE_VOL_EXCEEDS_LIMIT, "平单超出账户 "+getId()+" 当前持仓数量 "+currVolume+" : "+builder);
            }
        }
        }
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
