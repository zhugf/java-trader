package trader.service.event;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.primitives.Ints;
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
        private int eventType;
        private int[] filterMasks;
        private AsyncEventFilter[] filters;

        public AsyncEventHandler() {
            this.eventType = 0;
            filterMasks = new int[0];
            filters = new AsyncEventFilter[0];
        }

        public void addFilter(AsyncEventFilter filter, int eventMask) {
            List<AsyncEventFilter> filters0 = new ArrayList<>();
            List<Integer> filterMasks0 = new ArrayList<>();
            filters0.addAll(Arrays.asList(this.filters));
            filterMasks0.addAll(Ints.asList(filterMasks));
            filters0.add(filter);
            filterMasks0.add(eventMask);
            this.filters = filters0.toArray(new AsyncEventFilter[filters0.size()]);
            this.filterMasks = Ints.toArray(filterMasks0);
            this.eventType = eventMask & 0XFFFF0000;
        }

        @Override
        public void onEvent(AsyncEvent event, long sequence, boolean endOfBatch) throws Exception {
            int eventType = event.eventType;
            if ( (eventType&this.eventType)!=0 ) {
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

    private Map<String, AsyncEventHandler> handlersById = new HashMap<>();
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
        List<AsyncEventHandler> allFilters = new ArrayList<>();
        for(String chainId:FILTER_CHAINS) {
            var handler = new AsyncEventHandler();
            allFilters.add(handler);
            handlersById.put(chainId, handler);
        }
        AsyncEventHandler[] handlers = allFilters.toArray(new AsyncEventHandler[allFilters.size()]);
        disruptor.handleEventsWith(handlers);
        ringBuffer= disruptor.start();
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

    @Override
    public boolean addFilter(String filterChainId, AsyncEventFilter filter, int eventMask) {
        AsyncEventHandler handler = handlersById.get(filterChainId);
        boolean result=false;
        if ( null!=handler) {
            handler.addFilter(filter, eventMask);
            result = true;
        }
        return result;
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
