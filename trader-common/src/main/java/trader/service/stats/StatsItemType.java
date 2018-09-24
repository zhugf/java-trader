package trader.service.stats;

public enum StatsItemType {

    /**
     * 实时指标, 例如CPU
     */
    Instant

    /**
     * 累积指标, 例如处理事件数量
     */
    ,Cumulative
}
