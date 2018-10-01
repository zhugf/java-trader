package trader.service.trade;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;

import trader.service.ServiceConstants.ConnStatus;
import trader.service.trade.TradeConstants.TxnProvider;

/**
 * 抽象的交易通道
 */
public abstract class AbsTxnSession implements TxnSession {

    protected TradeServiceImpl tradeService;
    protected AccountImpl account;
    protected Logger logger;
    protected volatile ConnStatus status;
    protected long statusTime;

    public AbsTxnSession(TradeServiceImpl tradeService, AccountImpl account) {
        this.tradeService = tradeService;
        this.account = account;
        logger = LoggerFactory.getLogger(account.getLoggerPackage()+"."+AbsTxnSession.class.getSimpleName());
        status = ConnStatus.Initialized;
        statusTime = System.currentTimeMillis();
    }

    @Override
    public abstract TxnProvider getTxnProvider();

    @Override
    public ConnStatus getStatus() {
        return status;
    }

    public long getStatusTime() {
        return statusTime;
    }

    /**
     * 异步连接
     */
    public abstract void connect();

    protected abstract void closeImpl();

    public void close() {
        closeImpl();
    }

    protected void changeStatus(ConnStatus newStatus) {
        if ( newStatus!=status ) {
            ConnStatus lastStatus = status;
            status = newStatus;
            statusTime = System.currentTimeMillis();
            logger.info(account.getId()+" status changes from "+lastStatus+" to "+status);
            tradeService.onTxnSessionStatusChanged(account);
        }
    }

    @Override
    public JsonObject toJsonObject() {
        JsonObject json = new JsonObject();
        json.addProperty("status", status.name());
        json.addProperty("statusTime", statusTime);
        return json;
    }
}
