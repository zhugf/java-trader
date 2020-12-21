package trader.service.event;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;

import trader.common.beans.BeansContainer;
import trader.common.beans.Lifecycle;
import trader.common.config.ConfigUtil;
import trader.common.util.ConversionUtil;
import trader.service.md.MarketData;
import trader.service.util.ConcurrentUtil;

@Service
public class AsyncEventServiceImpl implements AsyncEventService, Lifecycle {

    public static final String ITEM_DISRUPTOR_WAIT_STRATEGY = "/AsyncEventService/disruptor/waitStrategy";
    public static final String ITEM_DISRUPTOR_RINGBUFFER_SIZE = "/AsyncEventService/disruptor/ringBufferSize";

    private static class AsyncEventHandler implements EventHandler<AsyncEvent>{

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
        }

        @Override
        public void onEvent(AsyncEvent event, long sequence, boolean endOfBatch) throws Exception {
            for(int i=0;i<filters.length;i++) {
                int filterMask = filterMasks[i];
                int eventType = event.eventType;
                if ( (eventType&filterMask)==eventType ) {
                    if ( filters[i].onEvent(event) ) {
                        break;
                    }
                }
            }
        }

    }

    @Autowired
    private ExecutorService executorService;

    private Disruptor<AsyncEvent> disruptor;
    private RingBuffer<AsyncEvent> ringBuffer;

    private List<Object[]> registeredFilters = new ArrayList<>();

    @Override
    public void init(BeansContainer beansContainer) throws Exception {
        //启动disruptor
        disruptor = new Disruptor<AsyncEvent>( new AsyncEventFactory()
            , ConfigUtil.getInt(ITEM_DISRUPTOR_RINGBUFFER_SIZE, 4096)
            , executorService
            , ProducerType.MULTI
            , ConcurrentUtil.createDisruptorWaitStrategy(ConfigUtil.getString(ITEM_DISRUPTOR_WAIT_STRATEGY))
            );
    }

    @Override
    public void destroy() {
        if ( ringBuffer!=null ) {
            try{
                disruptor.shutdown(5, TimeUnit.SECONDS);
            }catch(Throwable t) {
                disruptor.halt();
            }
            ringBuffer = null;
        }
    }

    public void start() {
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

    @Override
    public void publishMarketData(MarketData md) {
        long seq = ringBuffer.next();
        try {
            AsyncEvent event = ringBuffer.get(seq);
            event.setData(AsyncEvent.EVENT_TYPE_MARKETDATA, null, md,  null);
        }finally {
            ringBuffer.publish(seq);
        }
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
