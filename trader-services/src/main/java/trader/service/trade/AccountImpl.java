package trader.service.trade;

import java.io.File;
import java.util.*;

import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.core.FileAppender;
import trader.common.beans.BeansContainer;
import trader.common.config.ConfigUtil;
import trader.common.exchangeable.Exchangeable;
import trader.common.util.ConversionUtil;
import trader.common.util.JsonUtil;
import trader.common.util.StringUtil;
import trader.common.util.TraderHomeUtil;
import trader.service.ServiceConstants.AccountState;
import trader.service.trade.ctp.CtpTxnSession;

public class AccountImpl implements Account, TradeConstants {

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
    private List<PositionImpl> positions = new ArrayList<>();
    private Map<String, AccountViewImpl> views = new LinkedHashMap<>();
    private LinkedList<OrderImpl> orders = new LinkedList<>();

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
        return  feeEvaluator;
    }

    @Override
    public List<Order> getOrders(AccountView view) {
        return Collections.unmodifiableList(orders);
    }

    @Override
    public List<? extends Position> getPositions(AccountView view){
        return positions;
    }

    @Override
    public Map<String, AccountView> getViews() {
        return Collections.unmodifiableMap(views);
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
    public JsonObject toJsonObject() {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("loggerPackage", loggerPackage);
        json.add("txnSession", txnSession.toJsonObject());
        json.add("connectionProps", JsonUtil.props2json(connectionProps));
        return json;
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
            positions = txnSession.syncQryPositions();
            //分配持仓到View
            for(PositionImpl p:positions) {
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

}
