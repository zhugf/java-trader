package trader.common.exchangeable;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import trader.common.util.DateUtil;

/**
 * 某个交易品种在某个具体交易日的交易时间信息
 */
public class ExchangeableTradingTimes {
    private Exchangeable exchangeable;
    private LocalDate tradingDay;
    private int totalTradingSeconds;
    private LocalDateTime[] marketTimes;
    private List<LocalDateTime> stageBeginTimes;
    private int[] marketTimeSeconds;

    ExchangeableTradingTimes(Exchangeable exchangeable, LocalDate tradingDay, LocalDateTime[] marketTimes, List<LocalDateTime> stageBeginTimes){
        this.exchangeable = exchangeable;
        this.tradingDay = tradingDay;
        this.stageBeginTimes = stageBeginTimes;
        this.marketTimes = marketTimes;
        this.marketTimeSeconds = new int[marketTimes.length/2];
        totalTradingSeconds = 0;
        for(int i=0;i<marketTimes.length;i+=2) {
            LocalDateTime marketTimeStageBegin = marketTimes[i];
            LocalDateTime marketTimeStageEnd = marketTimes[i+1];
            Duration d = DateUtil.between(marketTimeStageBegin, marketTimeStageEnd);
            marketTimeSeconds[i/2] = (int)d.getSeconds();
            totalTradingSeconds += (int)d.getSeconds();
        }
    }

    public Exchangeable getExchangeable() {
        return exchangeable;
    }

    /**
     * 交易日
     */
    public LocalDate getTradingDay() {
        return tradingDay;
    }

    /**
     * 开市的总交易时长(秒)
     */
    public int getTotalTradingSeconds() {
        return totalTradingSeconds;
    }

    /**
     * 交易时间段
     */
    public LocalDateTime[] getMarketTimes() {
        return marketTimes;
    };

    public LocalDateTime getMarketOpenTime() {
        return marketTimes[0];
    }

    public LocalDateTime getMarketCloseTime() {
        return marketTimes[marketTimes.length-1];
    }

    public int[] getMarketTimeSeconds() {
        return marketTimeSeconds;
    }

    /**
     * 返回开市以来的时间(毫秒)
     */
    public int getTradingTime(LocalDateTime marketTime) {
        if ( marketTime.isBefore(marketTimes[0]) || compareTimeNoNanos(marketTime,marketTimes[marketTimes.length-1])>0) {
            return -1;
        }
        int result = 0;
        for(int i=0;i<marketTimes.length;i+=2) {
            LocalDateTime marketTimeStageBegin = marketTimes[i];
            LocalDateTime marketTimeStageEnd = marketTimes[i+1];
            Duration d = null;
            int compareResult = compareTimeNoNanos(marketTime, marketTimeStageEnd);
            if ( compareResult<=0 ) {
                d = DateUtil.between(marketTimeStageBegin, marketTime);
            }else {
                d = DateUtil.between(marketTimeStageBegin, marketTimeStageEnd);
            }
            long millis = d.getSeconds()*1000+d.getNano()/1000000;
            result += millis;
            if ( compareResult<=0 ) {
                break;
            }
        }
        return result;
    }

    /**
     * 市场时间段
     */
    public MarketTimeStage getTimeStage(LocalDateTime time) {
        for(int i=0;i<marketTimes.length;i+=2 ) {
            LocalDateTime frameBegin = marketTimes[i];
            LocalDateTime frameEnd = marketTimes[i+1];
            boolean isStageBegin = stageBeginTimes.contains(frameBegin);

            if ( isStageBegin ) {
                LocalDateTime auctionTime = frameBegin.minusMinutes(5);
                LocalDateTime marketBeforeOpenTime = auctionTime.minusMinutes(55);

                if ( time.isBefore(marketBeforeOpenTime) ){
                    return MarketTimeStage.MarketClose;
                } else {
                    if ( time.isBefore(auctionTime) ) {
                        return MarketTimeStage.BeforeMarketOpen;
                    } else {
                        if ( time.isBefore(frameBegin)){
                            return MarketTimeStage.AggregateAuction;
                        }
                    }
                }
            } else {
                if ( time.isBefore(frameBegin) ) {
                    return MarketTimeStage.MarketBreak;
                }
            }
            if ( compareTimeNoNanos(time, frameBegin)>=0 && compareTimeNoNanos(time,frameEnd)<=0 ) {
                return MarketTimeStage.MarketOpen;
            }
        }
        return MarketTimeStage.MarketClose;
    }

    private static int compareTimeNoNanos(LocalDateTime time1, LocalDateTime time2) {
        return time1.withNano(0).compareTo(time2.withNano(0));
    }

}
