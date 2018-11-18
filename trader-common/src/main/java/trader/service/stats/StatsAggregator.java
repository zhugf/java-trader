package trader.service.stats;

import java.util.List;

/**
 * Statistics item aggregation service
 * <BR>Only exists in coordinator service
 */
public interface StatsAggregator {

	/**
	 * aggregate stats item events
	 */
	public void aggregate(List<StatsItemPublishEvent> events);

	/**
	 * 返回合并后的统计数据
	 */
	public List<StatsItemAggregation> getAggregatedValues(String filter);

}
