package trader.common.exchangeable;

/**
 * 证券类型
 */
public enum ExchangeableType {
    /**
     * 指数
     */
    INDEX
    /**
     * 基金(含ETF)
     */
    ,FUND
    /**
     * 债券
     */
    ,BOND
    /**
     * 国债回购
     */
    ,BOND_REPURCHARSE
    /**
     * 可转债
     */
    ,CONVERTABLE_BOND
    /**
     * 股票
     */
    ,STOCK
    /**
     * 期货
     */
    ,FUTURE
    /**
     * 期货组合套利
     */
    ,FUTURE_COMBO
    /**
     * 期权
     */
    ,OPTION
    /**
     * 其他
     */
    ,OTHER
}
