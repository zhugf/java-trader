package trader.service.md;

import java.util.ArrayList;
import java.util.List;

public class MarketDataListenerHolder {

    private long lastTimestamp;
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

    public synchronized boolean checkTimestamp(long timestamp) {
        if ( timestamp<=lastTimestamp ) {
            return false;
        }
        lastTimestamp = timestamp;
        return true;
    }

}
