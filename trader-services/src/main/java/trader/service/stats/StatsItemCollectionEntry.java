package trader.service.stats;

import java.time.Instant;

/**
 * 采样数据的临时存储
 */
class StatsItemCollectionEntry {

    private StatsItem item;

    /**
     * last value update time for value field only
     */
    private long valueUpdateTime;

    /**
     * value, for external update only
     */
    private double value;

    /**
     * value getter interface, for internal stats item
     */
    private StatsItemValueGetter valueGetter;

    /**
     * last sampled value
     */
    private double sampledValue;

    /**
     * last sampled time
     */
    private long sampledTime;

    public StatsItemCollectionEntry(StatsItem item) {
        this.item = item;
    }

    public StatsItem getItem() {
        return item;
    }

    /**
     * Return true if valued was updated since last sample
     */
    public boolean isValueUpdated() {
        if (valueGetter != null) {
            return true;
        }
        if (valueUpdateTime>sampledTime) {
            return true;
        }
        return false;
    }

    public double getValue() {
        if (valueGetter != null) {
            return valueGetter.getValue(item);
        }
        return value;
    }

    public void addValue(long valueToAdd) {
        value += valueToAdd;
        valueUpdateTime = Instant.now().getEpochSecond();
    }

    public void setValue(double value) {
        this.value = value;
        valueUpdateTime = Instant.now().getEpochSecond();
    }

    public StatsItemValueGetter getValueGetter() {
        return valueGetter;
    }

    public void setValueGetter(StatsItemValueGetter valueGetter) {
        this.valueGetter = valueGetter;
    }

    public double getSampledValue() {
        return sampledValue;
    }

    public long getSampledTime() {
        return sampledTime;
    }

    /**
     * 采样数据
     */
    public StatsItemPublishEvent sample(long sampleTime) {
        sampledValue = getValue();
        this.sampledTime = sampleTime;
        if (sampledTime == 0) {
            this.sampledTime = Instant.now().getEpochSecond();
        }
        StatsItemPublishEvent result = new StatsItemPublishEvent();
        result.setItemInfo(item);
        result.setSampleTime(sampledTime);
        result.setSampleValue(sampledValue);
        return result;
    }

    /**
     * 采样数据
     */
    public StatsItemPublishEvent instantSample(long sampleTime) {
        StatsItemPublishEvent result = new StatsItemPublishEvent();
        result.setItemInfo(item);
        result.setSampleTime(sampleTime);
        result.setSampleValue(getValue());
        return result;
    }

}
