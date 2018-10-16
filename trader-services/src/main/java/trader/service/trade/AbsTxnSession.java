package trader.service.trade;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;

import trader.common.exception.AppException;
import trader.service.ServiceConstants.ConnState;
import trader.service.trade.TradeConstants.OrderState;
import trader.service.trade.TradeConstants.TxnProvider;

/**
 * 抽象的交易通道
 */
public abstract class AbsTxnSession implements TxnSession {

    protected TradeServiceImpl tradeService;
    protected AccountImpl account;
    protected Logger logger;
    protected volatile ConnState state;
    protected long stateTime;

    public AbsTxnSession(TradeServiceImpl tradeService, AccountImpl account) {
        this.tradeService = tradeService;
        this.account = account;
        logger = LoggerFactory.getLogger(account.getLoggerPackage()+"."+AbsTxnSession.class.getSimpleName());
        state = ConnState.Initialized;
        stateTime = System.currentTimeMillis();
    }

    @Override
    public abstract TxnProvider getTradeProvider();

    @Override
    public ConnState getState() {
        return state;
    }

    public long getStatusTime() {
        return stateTime;
    }

    /**
     * 异步连接
     */
    public abstract void connect();

    /**
     * 加载费率计算: 交易品种, 主力合约, 保证金率, 每跳幅度等等
     */
    public abstract TxnFeeEvaluator syncLoadFeeEvaluator() throws Exception;

    /**
     * 同步确认结算单
     */
    public abstract String syncConfirmSettlement() throws Exception ;

    /**
     * 同步查询账户接本账户数据
     */
    public abstract long[] syncQryAccounts() throws Exception;

    /**
     * 加载当前持仓品种, 并分配到AccountView.
     * <BR>注意, 查询中不得有在途交易, 否则会出现Position数据计算不对的问题
     */
    public abstract List<PositionImpl> syncQryPositions() throws Exception;

    /**
     * 发送报单
     */
    public abstract void asyncSendOrder(OrderImpl order) throws AppException;

    protected abstract void closeImpl();

    public void close() {
        closeImpl();
    }

    @Override
    public JsonObject toJsonObject() {
        JsonObject json = new JsonObject();
        json.addProperty("state", state.name());
        json.addProperty("stateTime", stateTime);
        return json;
    }

    /**
     * 由CTP实现类调用, 用于设置新状态
     */
    protected void changeState(ConnState newState) {
        if ( newState!=state ) {
            ConnState lastState = state;
            state = newState;
            stateTime = System.currentTimeMillis();
            logger.info(account.getId()+" status changes from "+lastState+" to "+state);
            tradeService.onTxnSessionStateChanged(account, lastState);
        }
    }

    /**
     * 由CTP实现类调用, 用于设置报单新状态
     */
    protected void changeOrderState(OrderImpl order, OrderState lastState) {
        account.onOrderStateChanged(order, lastState);
    }

}
