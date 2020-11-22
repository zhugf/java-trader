package trader.simulator;

import trader.service.trade.Account;
import trader.service.tradlet.AbsTradletGroupEngine;
import trader.service.tradlet.TradletGroupImpl;

/**
 * 模拟交易策略的事件驱动
 */
public class SimTradletGroupEngine extends AbsTradletGroupEngine {

    public SimTradletGroupEngine(TradletGroupImpl group){
        this.group = group;
    }

    @Override
    public void destroy() {
    }

    @Override
    public void queueEvent(int eventType, Object data) {
        try {
            processEvent(eventType, data);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
