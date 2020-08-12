package trader.service.trade;

import java.time.LocalDate;
import java.util.Collection;

import trader.common.exchangeable.Exchangeable;
import trader.common.util.JsonEnabled;
import trader.service.ServiceConstants.ConnState;
import trader.service.trade.TradeConstants.AccClassification;

public interface TxnSession extends JsonEnabled {

    public static final String PROVIDER_CTP = "ctp";

    public static final String PROVIDER_SIM = "sim";

    public String getProvider();

    public AccClassification getClassification();

    public ConnState getState();

    public LocalDate getTradingDay();

    /**
     * 唯一会话ID, 当连接上后, 从CTP API得到
     */
    public int getSessionId();

    /**
     * 同步查询所有的合约
     */
    public abstract Collection<Exchangeable> syncQueryInstruments() throws Exception;

}
