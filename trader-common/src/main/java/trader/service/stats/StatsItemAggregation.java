package trader.service.stats;

import java.util.Map;

public interface StatsItemAggregation{

    /**
     * 最后5分钟均值
     */
    public static final String KEY_LAST_5_MINUTE_AVG_VALUE = "last5MinAvgValue";

    /**
     * 最后15分钟均值
     */
    public static final String KEY_LAST_15_MINUTE_AVG_VALUE = "last15MinAvgValue";

    /**
     * 最后60分钟均值
     */
    public static final String KEY_LAST_60_MINUTE_AVG_VALUE = "last60MinAvgValue";
    /**
     * 最近值
     */
    public static final String KEY_LAST_VALUE = "lastValue";

    /**
     * 最后采样时间
     */
    public static final String KEY_LAST_SAMPLE_TIME = "lastSampleTime";

    public StatsItem getItem();

    /**
     * Last sample time on item node, in epoch seconds
     */
    public long getLastSampleTime();

    /**
     * Last aggregation time in epoch seconds.
     */
    public long getLastAggregateTime();

    public Map<String, Object> getAggregatedValues();

}
