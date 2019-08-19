package trader.service.md;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableTradingTimes;

public class MarketDataListenerHolder {
    private Exchangeable e;
    private ExchangeableTradingTimes tradingTimes;
    private long lastVolume;
    private long lastTimestamp;
    public MarketData lastData;
    private List<MarketDataListener> listeners = new ArrayList<>();

    MarketDataListenerHolder(Exchangeable e, LocalDate tradingDay){
        this.e = e;
        tradingTimes = e.exchange().getTradingTimes(e, tradingDay);
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

    /**
     * 检查切片时间戳, 只有比上次新的数据才允许.
     * <BR>CZCE的数据可能存在每秒多个TICK, 但是UpdateTime均为0的情况
     *
     * @return true 如果是新的行情切片数据, false 如果是已有的行情切片数据
     */
    public boolean checkTick(MarketData tick) {
        boolean result = false;
        long volume = tick.volume;
        if ( volume>lastVolume) {
            result = true;
            lastVolume = volume;
            //如果 timestamp 相同, 每次累加200ms
            if ( tick.updateTimestamp<=lastTimestamp ) {
                tick.updateTimestamp = lastTimestamp+200;
                tick.updateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(tick.updateTimestamp), tick.instrument.exchange().getZoneId()).toLocalDateTime();
            }
            lastTimestamp = tick.updateTimestamp;
        }
        return result;
    }

}
