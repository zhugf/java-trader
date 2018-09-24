package trader.common.exchangeable;

/**
 * 市场的时间阶段
 */
public enum MarketTimeStage{
    /**
     * 开市前
     */
    BeforeMarketOpen
    /**
     * 集合竞价
     */
    ,AggregateAuction
    /**
     * 正常开市
     */
    ,MarketOpen
    /**
     * 休息: 10:15-10:30, 11:30-13:00
     */
    ,MarketBreak
    /**
     * 收市
     */
    ,MarketClose
}
