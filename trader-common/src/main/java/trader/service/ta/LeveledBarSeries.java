package trader.service.ta;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.ta4j.core.BarSeries;

import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableTradingTimes;
import trader.common.tick.PriceLevel;

/**
 * 附带级别信息的K线数据
 */
public interface LeveledBarSeries extends BarSeries {

    public Exchangeable getInstrument();

    public PriceLevel getLevel();

    public FutureBar getBar2(int i);

    /**
     * 返回当天(交易日)的序列数据, 清除历史数据
     */
    public static LeveledBarSeries getDailySeries(LocalDate tradingDay, LeveledBarSeries series, boolean includeLast) {
        Exchangeable e = series.getInstrument();
        ExchangeableTradingTimes tradingTimes =  e.exchange().getTradingTimes(e, tradingDay);
        LocalDateTime mktOpenTime = tradingTimes.getMarketOpenTime();
        LocalDateTime mktCloseTime = tradingTimes.getMarketCloseTime();
        int beginIndex = -1; //inclusive
        int endIndex = -1; //exclusive
        int currIndex=series.getBeginIndex();

        while(currIndex<=series.getEndIndex()) {
            FutureBar bar = (FutureBar)series.getBar(currIndex);
            if ( beginIndex<0 && mktOpenTime.compareTo(bar.getBeginTime().toLocalDateTime())<=0 ) {
                beginIndex = currIndex;
                endIndex = beginIndex+1;
            } else if ( beginIndex>=0 && mktCloseTime.compareTo(bar.getEndTime().toLocalDateTime().withNano(0))>=0 ) {
                endIndex = currIndex+1;
            } else {
                if ( endIndex>0 ) {
                    break;
                }
            }
            currIndex++;
        }

        if ( !includeLast ) {
            endIndex--;
        }
        return (LeveledBarSeries)series.getSubSeries(beginIndex, endIndex);
    }
}
