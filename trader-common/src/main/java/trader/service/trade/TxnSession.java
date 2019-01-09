package trader.service.trade;

import java.time.LocalDate;

import trader.common.util.JsonEnabled;
import trader.service.ServiceConstants.ConnState;

public interface TxnSession extends JsonEnabled {

    public static final String PROVIDER_CTP = "ctp";

    public static final String PROVIDER_SIM = "sim";

    public String getProvider();

    public ConnState getState();

    public LocalDate getTradingDay();

    /**
     * 唯一会话ID, 当连接上后, 从CTP API得到
     */
    public int getSessionId();
}
