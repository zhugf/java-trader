package trader.service.stats;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 简单统计指标归并服务
 */
@Service
public class SimpleStatsAggregatorImpl implements StatsAggregator {

    private static final Logger logger = LoggerFactory.getLogger(SimpleStatsAggregatorImpl.class);

    private Map<String, StatsItemAggregationEntry> statsItemEntries = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {

    }

    @PreDestroy
    public void destroy() {

    }

    @Override
    public void aggregate(List<StatsItemPublishEvent> events) {
        for(StatsItemPublishEvent event:events) {
            StatsItemAggregationEntry entry = getOrCreateEntry(event.getItem());
            entry.aggregate(event.getSampleTime(), event.getSampleValue());
        }
    }

    @Override
    public List<StatsItemAggregation> getAggregatedValues(String filter) {
        List<StatsItemAggregation> result = new ArrayList<>();
        for(StatsItemAggregation agg:statsItemEntries.values()) {
            if ( filter==null || agg.getItem().getKey().indexOf(filter)>=0) {
                result.add(agg);
            }
        }
        return result;
    }

    @Scheduled(cron = "0 * * * * *")
    public void doAggregate() {

    }

    private StatsItemAggregationEntry getOrCreateEntry(StatsItem item)
    {
        String key = item.getKey();
        StatsItemAggregationEntry entry = statsItemEntries.get(key);
        if ( entry==null ) {
            entry = new StatsItemAggregationEntry(item);
            statsItemEntries.put(key, entry);
        }
        return entry;
    }
}
