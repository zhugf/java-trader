package trader.service.tradlet;

import java.util.Map;

import trader.service.tradlet.TradletEvent.EventType;

/**
 * 交易策略分组的单线程引擎, 每个对象必须独占一个线程
 */
public class TradletGroupEngine {
    private TradletGroupImpl group;
    private Thread engineThread;

    public TradletGroupEngine(TradletGroupImpl group) {
        this.group = group;
    }

    public TradletGroupImpl getGroup() {
        return group;
    }

    public Thread getEngineThread() {
        return engineThread;
    }

    /**
     * 策略线程
     */
    public void eventLoop() {
        engineThread = Thread.currentThread();
        try {

        } finally {
            engineThread = null;
        }
    }

    public void queueEvent(EventType eventType, Map groupConfig) {

    }

}
