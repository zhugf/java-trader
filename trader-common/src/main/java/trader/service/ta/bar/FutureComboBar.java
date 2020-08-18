package trader.service.ta.bar;

import java.time.LocalDateTime;

import trader.common.exchangeable.ExchangeableTradingTimes;
import trader.common.util.DateUtil;
import trader.service.md.MarketData;
import trader.service.ta.AbsFutureBar;
import trader.service.ta.LongNum;

/**
 * 组合KBar
 */
public class FutureComboBar extends AbsFutureBar {

    MarketData openTick2;
    MarketData closeTick2;
    MarketData maxTick2;
    MarketData minTick2;

    FutureComboBar(int index, ExchangeableTradingTimes tradingTimes, LocalDateTime beginTime, MarketData openTick1, MarketData openTick2){
        this.index = index;
        this.mktTimes = tradingTimes;
        this.volume = LongNum.ZERO;
        this.amount = LongNum.ZERO;
        setBeginTime(beginTime.atZone(tradingTimes.getInstrument().exchange().getZoneId()));
        this.beginAmount = LongNum.ZERO;
        this.beginVolume = LongNum.ZERO;

        this.endAmount = LongNum.ZERO;
        this.endVolume = LongNum.ZERO;
        this.endTime = this.beginTime;

        this.openTick = openTick1;
        this.openTick2 = openTick2;
        this.openPrice = LongNum.fromRawValue(openTick1.lastPrice-openTick2.lastPrice);
        this.mktAvgPrice = LongNum.fromRawValue(openTick1.averagePrice-openTick2.averagePrice);

        this.maxTick = openTick;
        this.maxTick2 = openTick2;
        this.highPrice = openPrice;

        this.minTick = openTick;
        this.minTick2 = openTick2;
        this.lowPrice = openPrice;

        update(openTick, openTick2, beginTime);
    }

    @Override
    public MarketData getOpenTick() {
        return null;
    }
    @Override
    public MarketData getCloseTick() {
        return null;
    }
    @Override
    public MarketData getMaxTick() {
        return null;
    }
    @Override
    public MarketData getMinTick() {
        return null;
    }

    public void update(MarketData endTick, MarketData endTick2, LocalDateTime endTime) {
        if ( endTime==null ) {
            endTime = DateUtil.max(endTick.updateTime, endTick2.updateTime);
        }
        this.closePrice = LongNum.fromRawValue(endTick.lastPrice-endTick2.lastPrice);
        this.closeTick = endTick;
        this.closeTick2 = endTick2;
        if ( this.closePrice.isGreaterThan(this.highPrice)) {
            this.highPrice = this.closePrice;
        }
        if ( this.closePrice.isLessThan(this.lowPrice)) {
            this.lowPrice = this.closePrice;
        }
        updateEndTime( endTime.atZone(endTick.instrument.exchange().getZoneId()));
    }

}
