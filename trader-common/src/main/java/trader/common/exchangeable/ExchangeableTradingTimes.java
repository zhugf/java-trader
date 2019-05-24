package trader.common.exchangeable;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import trader.common.exchangeable.ExchangeContract.MarketTimeSegment;
import trader.common.util.DateUtil;

/**
 * 某个交易品种在某个具体交易日的交易时间信息
 */
public class ExchangeableTradingTimes {
    static class MarketTimeSegmentInfo{
        public final MarketTimeSegment segment;
        public final LocalDateTime[] marketTimes;
        public final int totalMillis;
        public final int[] marketTimeMillis;
        public MarketTimeSegmentInfo(MarketTimeSegment segment, LocalDateTime[] marketTimes) {
            this.segment = segment;
            this.marketTimes = marketTimes;
            int totalMillis=0;
            marketTimeMillis = new int[marketTimes.length/2];
            for(int i=0;i<marketTimes.length;i+=2) {
                LocalDateTime t0 = marketTimes[i], t1=marketTimes[i+1];
                Duration d = DateUtil.between(t0, t1);
                marketTimeMillis[i/2] = (int)d.getSeconds()*1000;
                totalMillis += d.getSeconds()*1000;
            }
            this.totalMillis = totalMillis;
        }
    }

    private Exchangeable exchangeable;
    private LocalDate tradingDay;
    private int totalTradingMillis;
    private LocalDateTime[] marketTimes;

    /**
     * 不同市场分段的开始时间
     */
    private List<MarketTimeSegmentInfo> segmentInfos;
    private int[] marketTimeMillis;

    ExchangeableTradingTimes(Exchangeable exchangeable, LocalDate tradingDay, LocalDateTime[] marketTimes, List<MarketTimeSegmentInfo> segmentInfos){
        this.exchangeable = exchangeable;
        this.tradingDay = tradingDay;
        this.segmentInfos = segmentInfos;
        this.marketTimes = marketTimes;
        this.marketTimeMillis = new int[marketTimes.length/2];
        totalTradingMillis = 0;
        for(int i=0;i<marketTimes.length;i+=2) {
            LocalDateTime marketTimeStageBegin = marketTimes[i];
            LocalDateTime marketTimeStageEnd = marketTimes[i+1];
            Duration d = DateUtil.between(marketTimeStageBegin, marketTimeStageEnd);
            marketTimeMillis[i/2] = (int)d.getSeconds()*1000;
            totalTradingMillis += (int)d.getSeconds()*1000;
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
     * 开市的总交易时长(毫秒)
     */
    public int getTotalTradingMillis() {
        return totalTradingMillis;
    }

    /**
     * 某个交易时间段(日市/夜市)的总时长(毫秒)
     */
    public int getTotalTradingMillisInSegment(MarketType marketType) {
        for(MarketTimeSegmentInfo segInfo:segmentInfos) {
            if ( segInfo.segment.marketType!=marketType) {
                continue;
            }
            return segInfo.totalMillis;
        }
        return -1;
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
     * 返回当前的市场分段(日市/夜市)
     */
    public MarketType getSegmentType(LocalDateTime time) {
        for(MarketTimeSegmentInfo segInfo: this.segmentInfos) {
            LocalDateTime beginTime = segInfo.marketTimes[0].minusHours(1);
            LocalDateTime endTime = segInfo.marketTimes[segInfo.marketTimes.length-1];
            if ( time.compareTo(beginTime)>=0 && time.compareTo(endTime)<=0 ) {
                return segInfo.segment.marketType;
            }
        }
        return null;
    }

    /**
     * 返回市场分段(日市/夜市)的开始以来的时间(毫秒)
     */
    public int getTradingTimeInSegment(LocalDateTime time, MarketType marketType) {
        for(MarketTimeSegmentInfo segInfo: this.segmentInfos) {
            LocalDateTime beginTime = segInfo.marketTimes[0];
            LocalDateTime endTime = segInfo.marketTimes[segInfo.marketTimes.length-1];
            if ( time.compareTo(beginTime)>=0 && time.compareTo(endTime)<=0 && segInfo.segment.marketType==marketType) {
                LocalDateTime[] marketTimes = segInfo.marketTimes;
                int result=0;
                for(int i=0;i<marketTimes.length;i+=2) {
                    LocalDateTime t0 = marketTimes[i], t1=marketTimes[i+1];
                    if ( time.compareTo(t1)>=0 ) {
                        //如果超出当前交易时间小段
                        result += segInfo.marketTimeMillis[i/2];
                        continue;
                    }
                    if ( time.compareTo(t0)<=0 ) {
                        break;
                    }
                    if ( time.compareTo(t0)>=0 && time.compareTo(t1)<=0 ) {
                        Duration d = DateUtil.between(t0, time);
                        long millis = d.getSeconds()*1000+d.getNano()/1000000;
                        result += millis;
                        break;
                    }
                }
                return result;
            }
        }
        return -1;
    }

    /**
     * 市场时间段
     */
    public MarketTimeStage getTimeStage(LocalDateTime time) {
        for(int i=0;i<marketTimes.length;i+=2 ) {
            LocalDateTime frameBegin = marketTimes[i];
            LocalDateTime frameEnd = marketTimes[i+1];
            if ( isSegmentBeginTime(frameBegin) ) {
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

    private boolean isSegmentBeginTime(LocalDateTime time) {
        for(MarketTimeSegmentInfo info:segmentInfos) {
            if ( info.marketTimes[0].equals(time)) {
                return true;
            }
        }
        return false;
    }

    private static int compareTimeNoNanos(LocalDateTime time1, LocalDateTime time2) {
        return time1.withNano(0).compareTo(time2.withNano(0));
    }

}
