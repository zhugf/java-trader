package trader.common.exchangeable;

import java.time.LocalDate;
import java.time.LocalDateTime;

import trader.common.exchangeable.Exchange.MarketType;

public interface TradingMarketInfo {

    public LocalDate getTradingDay();

    public MarketType getMarket();

    public LocalDateTime[] getMarketTimes();

    public LocalDateTime getMarketOpenTime();

    public LocalDateTime getMarketCloseTime();

    public int getTradingSeconds();
}
