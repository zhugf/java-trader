package trader.service.md;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class MarketDataListenerHolder {

    private volatile long lastTimestamp;
    public volatile MarketData lastData;
    private List<MarketDataListener> listeners = new ArrayList<>();
    private Lock lock = new ReentrantLock();

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
     * 使用自旋锁
     */
    public boolean checkTimestamp(long timestamp) {
        if ( timestamp<=lastTimestamp ) {
            return false;
        }
        //SpinLock
        while(!lock.tryLock());
        try {
            if (timestamp <= lastTimestamp) {
                return false;
            }
            lastTimestamp = timestamp;
            return true;
        }finally {
            lock.unlock();
        }
    }

}
