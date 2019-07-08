package trader.service.tradlet;

import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;

import trader.common.beans.BeansContainer;
import trader.common.beans.Lifecycle;
import trader.common.config.ConfigUtil;
import trader.common.util.ConversionUtil;
import trader.common.util.StringUtil;
import trader.service.trade.AccountListener;
import trader.service.util.ConcurrentUtil;

/**
 * 交易策略分组的单线程引擎, 每个对象必须独占一个线程
 */
public class TradletGroupEngine extends AbsTradletGroupEngine implements Lifecycle, EventHandler<TradletEvent>, AccountListener {
    private static final Logger logger = LoggerFactory.getLogger(TradletGroupEngine.class);

    private Thread engineThread;

    private Disruptor<TradletEvent> disruptor;
    private RingBuffer<TradletEvent> ringBuffer;
    private volatile long lastEventTime;

    public TradletGroupEngine(TradletGroupImpl group) {
        this.group = group;
    }

    public Thread getEngineThread() {
        return engineThread;
    }

    @Override
    public void init(BeansContainer beansContainer) {
        super.init(beansContainer);

        ExecutorService executorService = beansContainer.getBean(ExecutorService.class);

        //读取Group特有配置, 如果不存在, 读取通用配置
        String ringBufferSizeStr = ConfigUtil.getString(TradletServiceImpl.ITEM_TRADLETGROUP+"#"+group.getId()+TradletServiceImpl.ITEM_SUFFIX_DISRUPTOR_RINGBUFFER_SIZE);
        if ( StringUtil.isEmpty(ringBufferSizeStr)) {
            ringBufferSizeStr = ConfigUtil.getString(TradletServiceImpl.ITEM_GLOBAL_DISRUPTOR_RINGBUFFER_SIZE);
        }
        String disruptorWaitStrategy = ConfigUtil.getString(TradletServiceImpl.ITEM_TRADLETGROUP+"#"+group.getId()+TradletServiceImpl.ITEM_SUFFIX_DISRUPTOR_WAIT_STRATEGY);
        if ( StringUtil.isEmpty(disruptorWaitStrategy)) {
            disruptorWaitStrategy = ConfigUtil.getString(TradletServiceImpl.ITEM_GLOBAL_DISRUPTOR_WAIT_STRATEGY);
        }
        int ringBufferSize = 4096;
        if ( !StringUtil.isEmpty(ringBufferSizeStr)) {
            ringBufferSize = ConversionUtil.toInt(ringBufferSizeStr);
        }
        disruptor = new Disruptor<TradletEvent>( new TradletEventFactory()
            , ringBufferSize
            , executorService
            , ProducerType.MULTI
            , ConcurrentUtil.createDisruptorWaitStrategy(disruptorWaitStrategy)
            );
        disruptor.handleEventsWith(this);
        ringBuffer = disruptor.start();

    }

    @Override
    public void destroy() {
        group.getAccount().removeAccountListener(this);
        if ( ringBuffer!=null ) {
            disruptor.halt();
            disruptor.shutdown();
            ringBuffer = null;
        }
    }

    @Override
    public void queueEvent(int eventType, Object data) {
        long seq = ringBuffer.next();
        try {
            TradletEvent event = ringBuffer.get(seq);
            event.setEvent(eventType, data);
        }finally {
            ringBuffer.publish(seq);
        }
    }

    @Override
    public void onEvent(TradletEvent event, long sequence, boolean endOfBatch) throws Exception {
        processEvent(event.eventType, event.data);
    }

}
