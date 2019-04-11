package trader.service.md;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableTradingTimes;

public class MarketDataListenerHolder {
    private Exchangeable e;
    private ExchangeableTradingTimes tradingTimes;
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
            var newListeners = new ArrayList<>(listeners);
            newListeners.add(listener);
            listeners = newListeners;
        }
    }

    public List<MarketDataListener> getListeners(){
        return listeners;
    }

    /**
     * 检查切片时间戳, 只有比上次新的数据才允许
     *
     * @return true 如果是新的行情切片数据, false 如果是已有的行情切片数据
     */
    public boolean checkTimestamp(long timestamp) {
        if ( timestamp<=lastTimestamp ) {
            return false;
        }
        lastTimestamp = timestamp;
        return true;
    }

}
