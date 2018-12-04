package trader.service.tradlet;

import java.util.List;
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
import trader.service.md.MarketData;
import trader.service.ta.LeveledTimeSeries;
import trader.service.util.ConcurrentUtil;

/**
 * 交易策略分组的单线程引擎, 每个对象必须独占一个线程
 */
public class TradletGroupEngine implements Lifecycle, EventHandler<TradletEvent> {
    private static final Logger logger = LoggerFactory.getLogger(TradletGroupEngine.class);

    private TradletServiceImpl tradletService;
    private BeansContainer beansContainer;
    private TradletGroupImpl group;
    private Thread engineThread;

    private Disruptor<TradletEvent> disruptor;
    private RingBuffer<TradletEvent> ringBuffer;

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
    public void init(BeansContainer beansContainer) {
        this.beansContainer = beansContainer;
        this.tradletService = beansContainer.getBean(TradletServiceImpl.class);
        ExecutorService executorService = beansContainer.getBean(ExecutorService.class);

        //读取Group特有配置, 如果不存在, 读取通用配置
        String disruptorRingBufferSize = ConfigUtil.getString(TradletServiceImpl.ITEM_TRADLETGROUP+"#"+group.getId()+TradletServiceImpl.ITEM_SUFFIX_DISRUPTOR_RINGBUFFER_SIZE);
        if ( StringUtil.isEmpty(disruptorRingBufferSize)) {
            disruptorRingBufferSize = ConfigUtil.getString(TradletServiceImpl.ITEM_GLOBAL_DISRUPTOR_RINGBUFFER_SIZE);
        }
        String disruptorWaitStrategy = ConfigUtil.getString(TradletServiceImpl.ITEM_TRADLETGROUP+"#"+group.getId()+TradletServiceImpl.ITEM_SUFFIX_DISRUPTOR_WAIT_STRATEGY);
        if ( StringUtil.isEmpty(disruptorWaitStrategy)) {
            disruptorWaitStrategy = ConfigUtil.getString(TradletServiceImpl.ITEM_GLOBAL_DISRUPTOR_WAIT_STRATEGY);
        }

        disruptor = new Disruptor<TradletEvent>( new TradletEventFactory()
            , ConversionUtil.toInt(disruptorRingBufferSize)
            , executorService
            , ProducerType.MULTI
            , ConcurrentUtil.createDisruptorWaitStrategy(disruptorWaitStrategy)
            );
        disruptor.handleEventsWith(this);
        ringBuffer = disruptor.start();
    }

    @Override
    public void destroy() {
        if ( ringBuffer!=null ) {
            disruptor.halt();
            disruptor.shutdown();
            ringBuffer = null;
        }
    }

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
        switch(event.eventType) {
        case TradletEvent.EVENT_TYPE_MD_TICK:
            onTick((MarketData)event.data);
            break;
        case TradletEvent.EVENT_TYPE_MD_BAR:
            onBar((LeveledTimeSeries)event.data);
            break;
        case TradletEvent.EVENT_TYPE_MISC_GROUP_UPDATE:
            onUpdateGroup((TradletGroupTemplate)event.data);
            break;
        }
    }

    private void onTick(MarketData md) {
        List<TradletHolder> tradletHolders = group.getTradletHolders();

        for(int i=0;i<tradletHolders.size();i++) {
            TradletHolder holder = tradletHolders.get(i);
            try{
                holder.getTradlet().onTick(md);
            }catch(Throwable t) {
                if ( holder.setThrowable(t) ) {
                    logger.error("策略组 "+group.getId()+" 运行策略 "+holder.getId()+" 失败: "+t.toString(), t);
                }
            }
        }
    }

    private void onBar(LeveledTimeSeries series) {
        List<TradletHolder> tradletHolders = group.getTradletHolders();

        for(int i=0;i<tradletHolders.size();i++) {
            TradletHolder holder = tradletHolders.get(i);
            try{
                holder.getTradlet().onNewBar(series);
            }catch(Throwable t) {
                if ( holder.setThrowable(t) ) {
                    logger.error("策略组 "+group.getId()+" 运行策略 "+holder.getId()+" 失败: "+t.toString(), t);
                }
            }
        }
    }

    /**
     * 更新TradletGroup配置
     */
    private void onUpdateGroup(TradletGroupTemplate template) {
        try{
            group.update(template);
        }catch(Throwable t) {
            logger.error("策略组 "+group.getId()+" 更新配置失败: "+t.toString(), t);
        }
    }

}
