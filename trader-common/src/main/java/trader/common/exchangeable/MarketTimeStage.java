package trader.common.exchangeable;

/**
 * 市场的时间阶段
 */
public enum MarketTimeStage{
    /**
     * 开市前1小时
     */
    BeforeMarketOpen
    /**
     * 集合竞价(日市夜市的开市前5分钟)
     */
    ,AggregateAuction
    /**
     * 正常开市
     */
    ,MarketOpen
    /**
     * 中场休息: 10:15-10:30, 11:30-13:00
     */
    ,MarketBreak
    /**
     * 收市
     */
    ,MarketClose
}
