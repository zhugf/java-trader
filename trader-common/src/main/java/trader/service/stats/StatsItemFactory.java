package trader.service.stats;

import java.util.Collection;

@FunctionalInterface
public interface StatsItemFactory {

    public Collection<StatsItem> getStatsItems();

}
