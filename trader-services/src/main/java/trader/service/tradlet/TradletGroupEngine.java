package trader.service.tradlet;

import java.util.Map;

import com.lmax.disruptor.EventHandler;

import trader.common.beans.BeansContainer;
import trader.common.beans.Lifecycle;
import trader.service.event.AsyncEvent;
import trader.service.md.MarketData;

/**
 * 交易策略分组的单线程引擎, 每个对象必须独占一个线程
 */
public class TradletGroupEngine implements Lifecycle, EventHandler<AsyncEvent> {
    private TradletServiceImpl tradletService;
    private BeansContainer beansContainer;
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

    @Override
    public void onEvent(AsyncEvent event, long sequence, boolean endOfBatch) throws Exception {

    }

    @Override
    public void init(BeansContainer beansContainer) {
        this.beansContainer = beansContainer;
        this.tradletService = beansContainer.getBean(TradletServiceImpl.class);
    }

    @Override
    public void destroy() {

    }

    public void queueMarketData(MarketData md) {

    }

    public void queueGroupUpdated(Map groupConfig) {

    }
}
