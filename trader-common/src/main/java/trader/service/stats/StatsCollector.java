package trader.service.stats;

/**
 * 服务统计指标的收集Service
 */
public interface StatsCollector {

    /**
     * 注册固定统计指标的回调函数
     * <BR>会每分钟调用一次, 获取实时值.
     */
    public void registerStatsItem(StatsItem item, StatsItemValueGetter itemValueGetter);

}
