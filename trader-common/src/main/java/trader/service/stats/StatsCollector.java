package trader.service.stats;

import java.util.List;

/**
 * Collect and send statistics item values to aggregation service every minute.
 * <BR>One publish collector service per jvm
 */
public interface StatsCollector {

    /**
     * 为统计指标的累积值+N
     * <BR>需要定时从外部主动调用
     */
    public void addStatsItemValue(StatsItem item, long itemValueToAdd);

    /**
     * 设置某个统计指标的实时值
     * <BR>需要定时从外部主动调用
     */
    public void setStatsItemValue(StatsItem item, double instantItemValue);

    /**
     * 注册固定统计指标的回调函数
     * <BR>会每分钟调用一次, 获取实时值.
     */
    public void registerStatsItem(StatsItem item, StatsItemValueGetter itemValueGetter);

    /**
     * 注册动态统计指标Factory
     */
    public void registerDynamicStatsItems(StatsItemFactory itemFactory);

    /**
     * 设置Collector对外发送的endpoint
     */
    public void setEndpoint(StatsPublishEndpoint endpoint);

    /**
     * 实时获取采样数据
     */
    public List<StatsItemPublishEvent> instantSample();
}
