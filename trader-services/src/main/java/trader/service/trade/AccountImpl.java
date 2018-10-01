package trader.service.trade;

import java.io.File;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.core.FileAppender;
import trader.common.util.ConversionUtil;
import trader.common.util.DateUtil;
import trader.common.util.JsonUtil;
import trader.common.util.StringUtil;
import trader.common.util.TraderHomeUtil;

public class AccountImpl implements Account {

    private String id;
    private String loggerPackage;
    private Logger logger;
    private AbsTxnSession txnSession;
    private Properties connectionProps;
    private Map<String, AccountViewImpl> views = new HashMap<>();
    private LinkedList<OrderImpl> orders = new LinkedList<>();

    public AccountImpl(Map elem) {
        id = ConversionUtil.toString(elem.get("id"));
        update(elem);
        createAccountLogger();
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public Properties getConnectionProps() {
        return connectionProps;
    }

    @Override
    public TxnSession getTxnSession() {
        return txnSession;
    }

    @Override
    public List<Order> getOrders(AccountView view) {
        return Collections.unmodifiableList(orders);
    }

    @Override
    public Map<String, AccountView> getViews() {
        return Collections.unmodifiableMap(views);
    }

    public void setViews(Map<String, AccountViewImpl> views0) {
        views = views0;
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

    public String getLoggerPackage() {
        return loggerPackage;
    }

    public void update(Map elem) {
        connectionProps = StringUtil.text2properties((String)elem.get("text"));
    }

    public void attachTxnSession(AbsTxnSession txnSession) {
        this.txnSession = txnSession;
    }

    private void createAccountLogger() {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();

        FileAppender fileAppender = new FileAppender();
        fileAppender.setContext(loggerContext);
        fileAppender.setName("timestamp");
        // set the file name
        fileAppender.setFile( (new File(TraderHomeUtil.getTraderHome(), "logs/trader-"+id+"-"+DateUtil.date2str(LocalDate.now())+".log")).getAbsolutePath());

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

}
