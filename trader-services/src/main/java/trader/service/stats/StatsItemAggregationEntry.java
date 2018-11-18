package trader.service.stats;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 采样数据的归并类
 */
public class StatsItemAggregationEntry implements StatsItemAggregation {
    private static final Logger logger = LoggerFactory.getLogger(StatsItemAggregationEntry.class);

    private static final MathContext VALUE_CONTEXT = new MathContext(2, RoundingMode.HALF_UP);

    /**
     * 最长采样数据保存时间: 1 hour
     */
    private static final int MAX_KEEP_SAMPLE_VALUE_SECONDS = 3600;

    private StatsItem item;

    private long adjustFactor;

    private long lastAggregateTime;

    /**
     * 根据顺序排列的采样数据列表
     */
    private LinkedList<SampleValueEntry> sampleValues = new LinkedList<>();

    private Map<String, Object> aggregatedValues = new HashMap<>();;

    public static class SampleValueEntry {

        /**
         * 采样时间
         */
        long sampleTime;

        /**
         * 原始值
         */
        double sampleValue;

        /**
         * 修正值: 只对可重新启动的累积值生效
         */
        double adjustedSampleValue;

        SampleValueEntry(long sampleTime, double sampleValue, double adjustedSampleValue)
        {
            this.sampleTime = sampleTime;
            this.sampleValue = sampleValue;
            this.adjustedSampleValue = adjustedSampleValue;
        }

        SampleValueEntry(long sampleTime, double sampleValue)
        {
            this(sampleTime, sampleValue, sampleValue);
        }

        /**
         * 采样时间(秒)
         */
        public long getTime() {
            return sampleTime;
        }

        /**
         * 调整后采样值
         */
        public double getValue() {
            return adjustedSampleValue;
        }
    }

    public StatsItemAggregationEntry(StatsItem item) {
        this.item = item;
    }

    @Override
    public StatsItem getItem() {
        return item;
    }

    @Override
    public long getLastAggregateTime() {
        return lastAggregateTime;
    }

    @Override
    public synchronized long getLastSampleTime() {
        if ( sampleValues.isEmpty()) {
            return 0;
        }
        return sampleValues.getLast().getTime();
    }

    @Override
    public Map<String, Object> getAggregatedValues() {
        return aggregatedValues;
    }

    public synchronized void aggregate(long sampleTime, double sampleValue)
    {
        preprocessSampleValue(sampleTime, sampleValue);

        //删除过期数据
        while(!sampleValues.isEmpty()) {
            SampleValueEntry first = sampleValues.peek();
            if ( (sampleTime - first.sampleTime)>MAX_KEEP_SAMPLE_VALUE_SECONDS ) {
                sampleValues.poll();
            }else {
                break;
            }
        }
        //重新计算
        if ( item.getType()==StatsItemType.Cumulative ) {
            aggregateCumulativeValues();
        }else {
            aggregateInstantValues();
        }
        lastAggregateTime = Instant.now().getEpochSecond();
    }

    private void preprocessSampleValue(long sampleTime, double sampleValue) {
        if ( item.getType()==StatsItemType.Cumulative && item.isCumulativeOnRestart()) {
            SampleValueEntry lastValue = sampleValues.peekLast();
            long sampleLong = (long)sampleValue;
            if ( lastValue!=null
                 && sampleLong>0
                 && (long)lastValue.getValue() > (sampleLong+adjustFactor) )
            {
                long prevFactor = adjustFactor;
                adjustFactor += sampleLong;
                logger.info("Stats item "+item.getKey()+" changed adjust factor to : "+adjustFactor+", prev: "+prevFactor+", added: "+sampleLong);
            }
            sampleValues.offer(new SampleValueEntry(sampleTime, sampleValue, (adjustFactor+sampleValue)));
        }else {
            sampleValues.offer(new SampleValueEntry(sampleTime, sampleValue));
        }
    }

    /**
     * 计算即时数据
     */
    private void aggregateInstantValues() {
        SampleValueEntry lastEntry = sampleValues.peekLast();
        if ( lastEntry==null ) {
            return;
        }
        aggregatedValues.put(KEY_LAST_5_MINUTE_AVG_VALUE, getInstantAvgValue(sampleValues, 5));
        aggregatedValues.put(KEY_LAST_15_MINUTE_AVG_VALUE, getInstantAvgValue(sampleValues, 15));
        aggregatedValues.put(KEY_LAST_60_MINUTE_AVG_VALUE, getInstantAvgValue(sampleValues, 60));
        aggregatedValues.put(KEY_LAST_VALUE, lastEntry.getValue());
    }

    /**
     * 计算累积数据
     */
    private void aggregateCumulativeValues() {
        SampleValueEntry lastEntry = sampleValues.peekLast();
        if ( lastEntry==null ) {
            return;
        }
        aggregatedValues.put(KEY_LAST_5_MINUTE_AVG_VALUE, getCumulativeAvgValuePerMinute(sampleValues, 5));
        aggregatedValues.put(KEY_LAST_15_MINUTE_AVG_VALUE, getCumulativeAvgValuePerMinute(sampleValues, 15));
        aggregatedValues.put(KEY_LAST_60_MINUTE_AVG_VALUE, getCumulativeAvgValuePerMinute(sampleValues, 60));
        aggregatedValues.put(KEY_LAST_VALUE, lastEntry.getValue());
    }

    /**
     * 计算方式: 统计区间内的数据, 计算平均数
     */
    private double getInstantAvgValue(LinkedList<SampleValueEntry> values, int averageMinutes)
    {
        double tv = 0;
        int tc = 0;
        SampleValueEntry lastEntry=null;
        for( Iterator<SampleValueEntry> it=values.descendingIterator(); it.hasNext(); ) {
            SampleValueEntry entry = it.next();
            if( lastEntry==null ) {
                lastEntry = entry;
            }
            if ( (lastEntry.getTime()-entry.getTime())>averageMinutes*60 ) {
                break;
            }
            tv += entry.getValue(); tc++;
        }
        BigDecimal v = new BigDecimal(tv, VALUE_CONTEXT);
        BigDecimal av = v.divide(new BigDecimal(tc), RoundingMode.HALF_UP);
        return av.doubleValue();
    }

    /**
     * 计算方式: 区间内开始结束两个数据, 计算差值的分钟平均.
     */
    private double getCumulativeAvgValuePerMinute(LinkedList<SampleValueEntry> values, int maxMinutes)
    {
        SampleValueEntry lastEntry = values.peekLast();
        SampleValueEntry firstEntry = lastEntry;

        for( Iterator<SampleValueEntry> it=values.descendingIterator(); it.hasNext(); ) { //从后向前逆序
            SampleValueEntry entry = it.next();
            if ( (lastEntry.getTime()-entry.getTime())>maxMinutes*60 ) {
                break;
            }
            firstEntry = entry;
        }
        BigDecimal v = new BigDecimal((lastEntry.getValue()-firstEntry.getValue()), VALUE_CONTEXT);
        long minutes = ((lastEntry.getTime()-firstEntry.getTime())+30) / 60;
        if ( minutes<=0 ) {
            minutes = 1;
        }
        BigDecimal av = v.divide(new BigDecimal(minutes), RoundingMode.HALF_UP);
        return av.doubleValue();
    }

}
