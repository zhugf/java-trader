package trader.common.exchangeable;

import java.time.LocalDate;
import java.time.LocalDateTime;

import trader.common.exchangeable.Exchange.MarketType;

/**
 * 交易时间信息
 */
public interface TradingMarketInfo {
    /**
     * 交易日
     */
    public LocalDate getTradingDay();

    /**
     * 开市的总交易时间(毫秒)
     */
    public int getTradingMillis();

    /**
     * 交易时间段
     */
    public LocalDateTime[] getMarketTimes();

    /**
     * 开市时间
     */
    public LocalDateTime getMarketOpenTime();

    /**
     * 闭市时间
     */
    public LocalDateTime getMarketCloseTime();

    /**
     * 市场类型: 日市/夜市
     */
    public MarketType getMarket();

    /**
     * 市场时间段
     */
    public MarketTimeStage getStage();

    /**
     * 市场从开市时间起, 计算交易时间(毫秒)
     */
    public int getTradingTime();
}
