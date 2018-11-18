package trader.api.stats;

import trader.service.stats.StatsItem;

public class StatsItemValueField {
    private StatsItem item;
    private double value;

    public StatsItem getItem() {
        return item;
    }
    public void setItem(StatsItem item) {
        this.item = item;
    }
    public double getValue() {
        return value;
    }
    public void setValue(double value) {
        this.value = value;
    }

}
