package trader.service.event;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;

import trader.common.beans.BeansContainer;
import trader.common.beans.ServiceEvent;
import trader.common.beans.ServiceEventHub;
import trader.common.beans.ServiceState;
import trader.common.config.ConfigUtil;
import trader.common.util.ConversionUtil;
import trader.service.md.MarketData;
import trader.service.util.ConcurrentUtil;

@Service
public class AsyncEventServiceImpl implements AsyncEventService {

    public static final String ITEM_DISRUPTOR_WAIT_STRATEGY = "disruptor.waitStrategy";
    public static final String ITEM_DISRUPTOR_RINGBUFFER_SIZE = "disruptor.ringBufferSize";

    private static class AsyncEventHandler implements EventHandler<AsyncEvent>{
        private int eventTypeHigh;
        private int[] filterMasks;
        private AsyncEventFilter[] filters;

        public AsyncEventHandler(List<Object[]> filters0) {
            filterMasks = new int[filters0.size()];
            filters = new AsyncEventFilter[filters0.size()];
            for(int i=0;i<filters0.size();i++) {
                Object[] filter = filters0.get(i);
                filterMasks[i] = ConversionUtil.toInt(filter[2]);
                filters[i] = (AsyncEventFilter)filter[1];
            }
            eventTypeHigh = filterMasks[0] & 0XFFFF0000;
        }

        @Override
        public void onEvent(AsyncEvent event, long sequence, boolean endOfBatch) throws Exception {
            int eventType = event.eventType;
            if ( (eventTypeHigh&eventType)==0 ) {
                for(int i=0;i<filters.length;i++) {
                    int filterMask = filterMasks[i];
                    if ( (eventType&filterMask)==eventType ) {
                        if ( !filters[i].onEvent(event) ) {
                            break;
                        }
                    }
                }
            }
        }

    }

    @Autowired
    private BeansContainer beansContainer;

    @Autowired
    private ExecutorService executorService;

    private Disruptor<AsyncEvent> disruptor;
    private RingBuffer<AsyncEvent> ringBuffer;

    private List<Object[]> registeredFilters = new ArrayList<>();
    private ServiceState state = ServiceState.NotInited;

    public ServiceState getState() {
        return state;
    }

    @PostConstruct
    public void init(){
        ServiceEventHub serviceEventHub = beansContainer.getBean(ServiceEventHub.class);
        serviceEventHub.registerServiceInitializer(getClass().getName(), ()->{
            return init0();
        });
        serviceEventHub.addListener((ServiceEvent event)->{
            startDisruptor();
        }, ServiceEventHub.TOPIC_SERVICE_ALL_INIT_DONE);
    }

    private AsyncEventService init0() {
        state = ServiceState.Starting;
        String configPrefix = AsyncEventService.class.getSimpleName()+".";
        //启动disruptor
        disruptor = new Disruptor<AsyncEvent>(()->{
                return new AsyncEvent();
            }, ConfigUtil.getInt(configPrefix+ITEM_DISRUPTOR_RINGBUFFER_SIZE, 4096)
            , executorService
            , ProducerType.MULTI
            , ConcurrentUtil.createDisruptorWaitStrategy(ConfigUtil.getString(configPrefix+ITEM_DISRUPTOR_WAIT_STRATEGY))
            );
        state = ServiceState.Ready;
        return this;
    }

    @PreDestroy
    public void destroy() {
        if (ringBuffer != null) {
            try {
                disruptor.shutdown(5, TimeUnit.SECONDS);
            } catch (Throwable t) {
                disruptor.halt();
            }
            ringBuffer = null;
        }
    }

    /**
     * 启动Distrputor
     */
    private void startDisruptor() {
        Map<String, List<Object[]>> filtersByChain = new LinkedHashMap<>();
        for(Object[] filter:registeredFilters) {
            String chainName = filter[0].toString();
            List<Object[]> filters = filtersByChain.get(chainName);
            if ( filters==null) {
                filters = new ArrayList<>();
                filtersByChain.put(chainName, filters);
            }
            filters.add(filter);
        }
        List<List<Object[]>> allFilters = new ArrayList<>(filtersByChain.values());
        AsyncEventHandler[] handlers = new AsyncEventHandler[filtersByChain.size()];
        for(int i=0;i<allFilters.size();i++) {
            handlers[i] = new AsyncEventHandler(allFilters.get(i));
        }
        disruptor.handleEventsWith(handlers);
        ringBuffer= disruptor.start();
        //为每个FilterChain启动独立的线程
    }

    @Override
    public void addFilter(String filterChainName, AsyncEventFilter filter, int eventMask) {
        registeredFilters.add(new Object[] {filterChainName, filter, eventMask});
    }

    public long publishEvent(int eventType, AsyncEventProcessor processor, Object data, Object data2)
    {
        long seq = ringBuffer.next();
        try {
            AsyncEvent event = ringBuffer.get(seq);
            event.setData(eventType, processor, data, data2);
        }finally {
            ringBuffer.publish(seq);
        }
        return seq;
    }

    @Override
    public void publishProcessorEvent(AsyncEventProcessor processor, int dataType, Object data, Object data2) {
        long seq = ringBuffer.next();
        try {
            AsyncEvent event = ringBuffer.get(seq);
            event.setData(AsyncEvent.EVENT_TYPE_PROCESSOR|dataType, processor, data,  data2);
        }finally {
            ringBuffer.publish(seq);
        }
    }

}
