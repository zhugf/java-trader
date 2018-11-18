package trader.service.stats;

public class StatsItemPublishEvent {
    private StatsItem item;
    private double sampleValue;
    private long sampleTime;

    public double getSampleValue() {
        return sampleValue;
    }

    public void setSampleValue(double v) {
        this.sampleValue = v;
    }

    public long getSampleTime() {
        return sampleTime;
    }

    public void setSampleTime(long t) {
        this.sampleTime = t;
    }

    public StatsItem getItem() {
        return item;
    }

    public void setItemInfo(StatsItem itemInfo) {
        this.item = itemInfo;
    }

}
