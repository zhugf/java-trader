package trader.service.trade;

import java.time.LocalDate;

import trader.common.util.JsonEnabled;
import trader.service.ServiceConstants.ConnState;

public interface TxnSession extends JsonEnabled {

    public static final String PROVIDER_CTP = "ctp";

    public String getProvider();

    public ConnState getState();

    public LocalDate getTradingDay();
}
