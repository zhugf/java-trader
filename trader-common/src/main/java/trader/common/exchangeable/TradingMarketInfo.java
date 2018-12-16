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

    /**
     * 市场从开市时间起, 计算交易时间(毫秒)
     */
    public int getTradingTime();

    /**
     * 市场时间段
     */
    public MarketTimeStage getMarketTimeStage();
}
