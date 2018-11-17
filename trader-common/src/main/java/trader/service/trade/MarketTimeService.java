package trader.service.trade;

import java.time.LocalDate;
import java.time.LocalDateTime;

public interface MarketTimeService {

    /**
     * 市场时间
     */
    public LocalDateTime getMarketTime();

    /**
     * 交易日
     */
    public LocalDate getMarketDay();

}
