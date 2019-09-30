package trader.service.ta;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.ta4j.core.Bar;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.num.Num;

import trader.common.exchangeable.Exchange;
import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableTradingTimes;
import trader.common.exchangeable.MarketDayUtil;
import trader.common.util.PriceUtil;

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

    /**
     * 返回两个Bar之间的市场时间, 单位毫秒
     */
    public static long getBarsDuration(Bar2 bar, Bar2 bar2) {

        ExchangeableTradingTimes tradingTimes = bar.getTradingTimes();
        ExchangeableTradingTimes tradingTimes2 = bar2.getTradingTimes();
        Exchangeable instrument = tradingTimes.getInstrument();
        Exchange exchange = tradingTimes.getInstrument().exchange();

        LocalDateTime beginTime = bar.getEndTime().toLocalDateTime();
        LocalDateTime endTime = bar2.getBeginTime().toLocalDateTime();

        long result = 0;
        long endMktMillis = tradingTimes.getTradingTime(endTime);
        long beginMktMillis = tradingTimes.getTradingTime(beginTime);
        if( tradingTimes.getTradingDay().equals(tradingTimes2.getTradingDay())) {
            //相同交易日
            result = Math.abs(endMktMillis-beginMktMillis);
        } else {
            //隔日, 第一天计算从第一个Bar到收市
            result = (tradingTimes.getTotalTradingMillis()-beginMktMillis);
            //计算整天
            LocalDate tradingDay = MarketDayUtil.nextMarketDay(exchange, tradingTimes.getTradingDay());
            while(!tradingDay.equals(tradingTimes2.getTradingDay())) {
                ExchangeableTradingTimes currTradingTimes = exchange.getTradingTimes(instrument, tradingDay);
                result += (currTradingTimes.getTotalTradingMillis());
            }
            //计算最后一个Bar
            result += (endMktMillis);
        }

        return result;
    }

    public static long getBarHeight(Bar bar) {
        Num max = bar.getMaxPrice(), min = bar.getMinPrice();
        Num height = max.minus(min);

        long result = 0;
        if ( height instanceof LongNum ) {
            result = ((LongNum)height).rawValue();
        }else {
            result = PriceUtil.price2long(height.doubleValue());
        }
        return result;
    }

}
