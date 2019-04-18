package trader.service.ta.bar;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedList;

import org.ta4j.core.num.Num;

import trader.common.util.PriceUtil;
import trader.service.md.MarketData;
import trader.service.ta.Bar2;
import trader.service.ta.LongNum;

/**
 * 固定滑动窗口的BAR, 滑动窗口可以是时间或成交量或金额
 */
public abstract class SlidingWindowTicksBar implements Bar2 {

    protected LongNum open;
    protected LongNum close;
    protected LongNum max;
    protected LongNum min;
    protected LongNum volume;
    protected LongNum amount;

    protected LongNum avgPrice;
    protected LongNum mktAvgPrice;
    protected Duration duration;
    /** End time of the bar */
    protected ZonedDateTime endTime;
    /** Begin time of the bar */
    protected ZonedDateTime beginTime;

    protected MarketData openTick;
    protected MarketData closeTick;
    protected MarketData maxTick;
    protected MarketData minTick;
    protected long openInt;
    protected LinkedList<MarketData> ticks = new LinkedList<>();
    /**
     * 是否需要重新计算MinMax
     */
    protected boolean edgeDirty;

    @Override
    public Num getOpenPrice() {
        return open;
    }

    @Override
    public Num getMinPrice() {
        if( edgeDirty ) {
             computeMinMax();
        }
        return min;
    }

    @Override
    public Num getMaxPrice() {
        if( edgeDirty ) {
            computeMinMax();
       }
        return max;
    }

    @Override
    public Num getClosePrice() {
        return close;
    }

    @Override
    public Num getVolume() {
        return volume;
    }

    @Override
    public int getTrades() {
        return -1;
    }

    @Override
    public Num getAmount() {
        return amount;
    }

    @Override
    public Duration getTimePeriod() {
        if ( duration==null ) {
            duration = Duration.ofMillis(closeTick.mktTime-openTick.mktTime);
        }
        return duration;
    }

    @Override
    public ZonedDateTime getBeginTime() {
        return beginTime;
    }

    @Override
    public ZonedDateTime getEndTime() {
        return endTime;
    }

    @Override
    public void addTrade(Num tradeVolume, Num tradePrice) {
        throw new UnsupportedOperationException("addTrade");
    }

    @Override
    public void addPrice(Num price) {
        throw new UnsupportedOperationException("addPrice");
    }

    @Override
    public Num getAvgPrice() {
        return avgPrice;
    }

    @Override
    public Num getMktAvgPrice() {
        return mktAvgPrice;
    }

    @Override
    public long getOpenInterest() {
        return openInt;
    }

    @Override
    public MarketData getOpenTick() {
        return openTick;
    }

    @Override
    public MarketData getCloseTick() {
        return closeTick;
    }

    @Override
    public MarketData getMaxTick() {
        if( edgeDirty ) {
            computeMinMax();
        }
        return maxTick;
    }

    @Override
    public MarketData getMinTick() {
        if( edgeDirty ) {
            computeMinMax();
        }
        return minTick;
    }

    /**
     * 更新滑动窗口
     */
    public void update(MarketData newTick) {
        ZoneId zoneId = newTick.instrumentId.exchange().getZoneId();
        int ticksBeforeUpdate = ticks.size();
        if ( updateTicks(newTick) || ticksBeforeUpdate==0 ) {
            edgeDirty = true;
            LinkedList<MarketData> ticks = this.ticks;
            //全部重新计算Begin/Close/Max/Min
            MarketData tick0 = ticks.getFirst();
            openTick = tick0;
            long open = openTick.lastPrice;
            long max = open, min = open;
            MarketData maxTick=tick0, minTick=tick0;
            for(MarketData tick:ticks) {
                if ( tick0.highestPrice!=tick.highestPrice && PriceUtil.isValidPrice(tick.highestPrice)) {
                    max = tick.highestPrice;
                    maxTick = tick;
                }else if ( tick.lastPrice>max) {
                    max = tick.lastPrice;
                    maxTick = tick;
                }

                if ( tick0.lowestPrice!=tick.lowestPrice && PriceUtil.isValidPrice(tick.lowestPrice) ) {
                    min = tick.lowestPrice;
                    minTick = tick;
                }else if ( tick.lastPrice<min ) {
                    min = tick.lastPrice;
                    minTick = tick;
                }
                tick0 = tick;
            }
            this.open = new LongNum(open);
            this.openTick = ticks.getFirst();
            this.beginTime = this.openTick.updateTime.atZone(zoneId);
        }
        //增量更新, 检查一下Close/Max/Min就算了
        long lastPrice = newTick.lastPrice, highestPrice=newTick.highestPrice, lowestPrice=newTick.lowestPrice;

        MarketData lastTick = closeTick;
        openInt = newTick.openInterest;
        mktAvgPrice = new LongNum(newTick.averagePrice);
        closeTick = newTick;
        endTime = newTick.updateTime.atZone(zoneId);
        close = new LongNum(lastPrice);
        volume = new LongNum(PriceUtil.price2long(newTick.volume-openTick.volume));
        amount = new LongNum(newTick.turnover-openTick.turnover);
        duration = null;
        //如果窗口没有滑动,且MinMax有效 那么只需检查 lastClose-newClose之间的差值, 就可以更新MinMax
        if ( !edgeDirty ) {
            if ( lastTick.highestPrice!=highestPrice && PriceUtil.isValidPrice(highestPrice)) {
                this.max = new LongNum(highestPrice);
                this.maxTick = newTick;
            }else if ( lastPrice>this.max.rawValue() ) {
                this.max = new LongNum(lastPrice);
                this.maxTick = newTick;
            }
            if ( lastTick.lowestPrice!=lowestPrice && PriceUtil.isValidPrice(lowestPrice)) {
                this.min = new LongNum(lowestPrice);
                this.minTick = newTick;
            }else if ( lastPrice<this.min.rawValue() ) {
                this.min = new LongNum(lastPrice);
                this.minTick = newTick;
            }
        }
    }

    /**
     * 按需重新计算Min/Max
     */
    private void computeMinMax() {
        LinkedList<MarketData> ticks = this.ticks;
        MarketData tick0 = ticks.getFirst();
        long open = openTick.lastPrice;
        long max = open, min = open;
        MarketData maxTick=tick0, minTick=tick0;
        for(MarketData tick:ticks) {
            if ( tick0.highestPrice!=tick.highestPrice && PriceUtil.isValidPrice(tick.highestPrice)) {
                max = tick.highestPrice;
                maxTick = tick;
            }else if ( tick.lastPrice>max) {
                max = tick.lastPrice;
                maxTick = tick;
            }

            if ( tick0.lowestPrice!=tick.lowestPrice && PriceUtil.isValidPrice(tick.lowestPrice) ) {
                min = tick.lowestPrice;
                minTick = tick;
            }else if ( tick.lastPrice<min ) {
                min = tick.lastPrice;
                minTick = tick;
            }
            tick0 = tick;
        }

        this.max = new LongNum(max);
        this.min = new LongNum(min);
        this.maxTick = maxTick;
        this.minTick = minTick;
        edgeDirty = false;
    }

    /**
     * 实际更新ticks列表, 如果有滑动窗口调整返回true
     */
    protected abstract boolean updateTicks(MarketData tick);

}
