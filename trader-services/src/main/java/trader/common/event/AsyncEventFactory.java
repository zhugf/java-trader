package trader.common.event;

import com.lmax.disruptor.EventFactory;

public class AsyncEventFactory implements EventFactory<AsyncEvent> {

    @Override
    public AsyncEvent newInstance() {
        return new AsyncEvent();
    }

}
