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
import trader.service.ServiceConstants.AccountState;
import trader.service.md.MarketData;
import trader.service.ta.LeveledTimeSeries;
import trader.service.trade.Account;
import trader.service.trade.AccountListener;
import trader.service.trade.Order;
import trader.service.trade.OrderStateTuple;
import trader.service.trade.Transaction;
import trader.service.util.ConcurrentUtil;

/**
 * 交易策略分组的单线程引擎, 每个对象必须独占一个线程
 */
public class TradletGroupEngine implements Lifecycle, EventHandler<TradletEvent>, AccountListener {
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

        //关联TradletGroup到Account
        group.setState(TradletGroup.State.Enabled);
        group.getAccountView().getAccount().addAccountListener(this);
    }

    @Override
    public void destroy() {
        group.getAccountView().getAccount().removeAccountListener(this);
        if ( ringBuffer!=null ) {
            disruptor.halt();
            disruptor.shutdown();
            ringBuffer = null;
        }
    }

    //--------- AccountListener--------
    /**
     * 响应账户状态, 修改TradletGroup的状态
     */
    @Override
    public void onAccountStateChanged(Account account, AccountState oldState) {
        if ( account.getState()==AccountState.Ready) {
            group.setState(TradletGroup.State.Enabled);
        } else {
            group.setState(TradletGroup.State.Suspended);
        }
    }

    /**
     * 排队报单回报事件到处理队列
     */
    @Override
    public void onOrderStateChanged(Account account, Order order, OrderStateTuple lastStateTuple) {
        queueEvent(TradletEvent.EVENT_TYPE_TRADE_ORDER, order);
    }

    /**
     * 排队成交事件到处理队列
     */
    @Override
    public void onTransaction(Account account, Transaction txn) {
        queueEvent(TradletEvent.EVENT_TYPE_TRADE_TXN, txn);
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
        case TradletEvent.EVENT_TYPE_TRADE_ORDER:
            onOrder((Order)event.data);
            break;
        case TradletEvent.EVENT_TYPE_TRADE_TXN:
            onTransaction((Transaction)event.data);
            break;
        default:
            logger.error("Unsupported event type "+Integer.toHexString(event.eventType)+", data: "+event.data);
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
     * 报单回报
     */
    private void onOrder(Order data) {

    }

    /**
     * 报单回报
     */
    private void onTransaction(Transaction txn) {

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
