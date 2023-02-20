package trader.service.md;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import trader.common.exchangeable.Exchange;
import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableTradingTimes;

public class MarketDataRuntimeData {
    private static final int RECENT_DATA_DEPTH = 10;
    private Exchangeable instrument;
    private ExchangeableTradingTimes tradingTimes;
    /**
     * 逆序TICK数据
     */
    private LinkedList<MarketData> recentDatas = new LinkedList<>();
    private long lastVolume;
    private long lastTimestamp;
    private MarketData lastData;
    private List<MarketDataListener> listeners = new ArrayList<>();

    MarketDataRuntimeData(Exchangeable e, LocalDate tradingDay){
        this.instrument = e;
        tradingTimes = e.exchange().getTradingTimes(e, tradingDay);
    }

    public Exchangeable getInstrument() {
        return instrument;
    }

    public ExchangeableTradingTimes getTradingTimes() {
        return tradingTimes;
    }

    public void addListener(MarketDataListener listener) {
        if ( !listeners.contains(listener) ) {
            List<MarketDataListener> newListeners = new ArrayList<>(listeners);
            newListeners.add(listener);
            listeners = newListeners;
        }
    }

    public List<MarketDataListener> getListeners(){
        return listeners;
    }

    public MarketData getLastData() {
        return lastData;
    }

    /**
     * 检查切片时间戳, 只有比上次新的数据才允许.
     * <BR>CZCE的数据可能存在每秒多个TICK, 但是UpdateTime均为0的情况
     *
     * @return true 如果是新的行情切片数据, false 如果是已有的行情切片数据
     */
    public boolean checkTick(MarketData tick) {
        boolean result = false;
        long tickVolume = tick.volume;
        if ( tickVolume>lastVolume || tick.updateTimestamp>lastTimestamp ) {
            //时间戳在后
            result = true;
        }

        if ( !result
                && instrument.exchange()==Exchange.CZCE
                && tick.updateTimestamp>=lastTimestamp
                && tickVolume>=lastVolume )
        {
            //CZCE一秒以内的时间戳会相等, 这时候检查volume/ask/bidvol
            result = true;
            for(MarketData rTick:recentDatas) {
                if ( MarketData.equals(rTick, tick)) {
                    result = false;
                    break;
                }
            }
        }

        if ( result ) {
            //如果 timestamp 相同, 每次累加 200ms
            if ( tick.updateTimestamp<=lastTimestamp ) {
                tick.updateTimestamp = lastTimestamp+200;
                tick.updateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(tick.updateTimestamp), tick.instrument.exchange().getZoneId()).toLocalDateTime();
            }
            lastVolume = tickVolume;
            lastTimestamp = tick.updateTimestamp;
            this.lastData = tick;
            this.recentDatas.offerFirst(tick);
            while(recentDatas.size()>RECENT_DATA_DEPTH) {
                recentDatas.pollLast();
            }
            result = true;
        }
        return result;
    }

}
