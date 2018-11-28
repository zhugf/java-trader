package trader.service.md;

import java.util.ArrayList;
import java.util.List;

public class MarketDataListenerHolder {
    private long lastTimestamp;
    public MarketData lastData;
    private List<MarketDataListener> listeners = new ArrayList<>();

    MarketDataListenerHolder(){

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
