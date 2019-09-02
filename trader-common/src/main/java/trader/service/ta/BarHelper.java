package trader.service.ta;

import org.ta4j.core.Bar;
import org.ta4j.core.TimeSeries;

/**
 * Bar的一些辅助函数
 */
public class BarHelper {

    /**
     * 从后向前找最高价的Bar
     *
     * @param series 序列
     * @param endIdx 最后一个包含的Bar index(包含), -1 代表最后一个
     * @param lastN 最后N个Bar中找
     */
    public static int lastHighest(TimeSeries series, int endIdx, int lastN) {
        if ( endIdx<0 ) {
            endIdx = series.getBarCount() - 1;
        }
        int beginIdx = endIdx - lastN-1;
        if (beginIdx<0) {
            beginIdx = 0;
        }
        Bar maxBar = series.getBar(beginIdx);
        int result = beginIdx;
        for(int i=beginIdx; i<=endIdx; i++) {
            Bar bar0 = series.getBar(i);
            if ( bar0.getMaxPrice().isGreaterThan(maxBar.getMaxPrice())) {
                maxBar = bar0;
                result = i;
            }
        }
        return result;
    }

    /**
     * 从后向前找最低价的Bar
     *
     * @param series 序列
     * @param endIdx 最后一个包含的Bar index(包含), -1 代表最后一个
     * @param lastN 最后N个Bar中找
     *
     * @return
     */
    public static int lastLowest(LeveledTimeSeries series, int endIdx, int lastN) {
        if ( endIdx<0 ) {
            endIdx = series.getBarCount() - 1;
        }
        int beginIdx = endIdx - lastN-1;
        if (beginIdx<0) {
            beginIdx = 0;
        }
        Bar minBar = series.getBar(beginIdx);
        int result = beginIdx;
        for(int i=beginIdx; i<=endIdx; i++) {
            Bar bar0 = series.getBar(i);
            if ( bar0.getMinPrice().isLessThan(minBar.getMinPrice())) {
                minBar = bar0;
                result = i;
            }
        }
        return result;
    }

}
