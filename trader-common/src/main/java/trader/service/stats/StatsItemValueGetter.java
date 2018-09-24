package trader.service.stats;

@FunctionalInterface
public interface StatsItemValueGetter {

    public double getValue(StatsItem itemInfo);

}
