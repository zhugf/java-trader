package trader.service.tradlet;

import com.lmax.disruptor.EventFactory;

public class TradletEventFactory implements EventFactory<TradletEvent> {

    @Override
    public TradletEvent newInstance() {
        return new TradletEvent();
    }

}
