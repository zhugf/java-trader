package trader.service.tradlet;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import trader.common.beans.BeansContainer;
import trader.common.beans.Lifecycle;
import trader.common.exchangeable.Exchangeable;
import trader.service.ServiceConstants.AccountState;
import trader.service.md.MarketData;
import trader.service.ta.LeveledTimeSeries;
import trader.service.ta.TAListener;
import trader.service.ta.TAService;
import trader.service.trade.Account;
import trader.service.trade.AccountListener;
import trader.service.trade.MarketTimeService;
import trader.service.trade.Order;
import trader.service.trade.OrderStateTuple;
import trader.service.trade.Transaction;

/**
 * TradletGroupEngine公共类
 */
public abstract class AbsTradletGroupEngine implements TradletConstants, Lifecycle, AccountListener {
    private static final Logger logger = LoggerFactory.getLogger(AbsTradletGroupEngine.class);

    protected TradletService tradletService;
    protected BeansContainer beansContainer;
    protected MarketTimeService mtService;
    protected TradletGroupImpl group;
    protected long lastEventTime;

    public TradletGroupImpl getGroup() {
        return group;
    }

    public long getLastEventTime() {
        return lastEventTime;
    }

    @Override
    public void init(BeansContainer beansContainer) {
        this.beansContainer = beansContainer;
        this.tradletService = beansContainer.getBean(TradletServiceImpl.class);
        mtService = beansContainer.getBean(MarketTimeService.class);
        group.initTradlets();
        group.getUpdatedInstruments();
        //关联TradletGroup到Account
        group.setState(TradletGroupState.Enabled);
        if (group.getAccount()!=null) {
            group.getAccount().addAccountListener(this);
        }
        TAService taService = beansContainer.getBean(TAService.class);
        taService.registerListener(group.getInstruments(), group.getPriceLevels(), new TAListener() {
            @Override
            public void onNewBar(Exchangeable e, LeveledTimeSeries series) {
                queueEvent(TradletEvent.EVENT_TYPE_MD_BAR, series);
            }
        });
    }

    //--------- AccountListener--------
    /**
     * 响应账户状态, 修改TradletGroup的状态
     */
    @Override
    public void onAccountStateChanged(Account account, AccountState oldState) {
        if ( account.getState()==AccountState.Ready) {
            group.setState(TradletGroupState.Enabled);
        } else {
            group.setState(TradletGroupState.Suspended);
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

    /**
     * 排队处理TradletGroup事件
     */
    public abstract void queueEvent(int eventType, Object data);

    protected void processEvent(int eventType, Object data) throws Exception {
        lastEventTime = mtService.currentTimeMillis();
        if ( logger.isDebugEnabled() ) {
            logger.debug("Tradlet group "+group.getId()+" process event: "+ String.format("%08X", eventType)+" data "+data);
        }
        switch(eventType) {
        case TradletEvent.EVENT_TYPE_MD_TICK:
            processTick((MarketData)data);
            break;
        case TradletEvent.EVENT_TYPE_MD_BAR:
            processBar((LeveledTimeSeries)data);
            break;
        case TradletEvent.EVENT_TYPE_MISC_GROUP_RELOAD:
            processReloadGroup((TradletGroupTemplate)data);
            break;
        case TradletEvent.EVENT_TYPE_TRADE_ORDER:
            processOrder((Order)data);
            break;
        case TradletEvent.EVENT_TYPE_TRADE_TXN:
            processTransaction((Transaction)data);
            break;
        case TradletEvent.EVENT_TYPE_MISC_NOOP:
            processNoop();
            break;
        default:
            logger.error("Unsupported event type "+Integer.toHexString(eventType)+", data: "+data);
        }
    }

    protected void processTick(MarketData md) {
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

    protected void processBar(LeveledTimeSeries series) {
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
    private void processOrder(Order data) {
        group.updateOnOrder(data);
    }

    /**
     * 报单回报
     */
    private void processTransaction(Transaction txn) {
        group.updateOnTxn(txn);
    }

    private void processNoop() {
        List<TradletHolder> tradletHolders = group.getTradletHolders();

        for(int i=0;i<tradletHolders.size();i++) {
            TradletHolder holder = tradletHolders.get(i);
            try{
                holder.getTradlet().onNoopSecond();
            }catch(Throwable t) {
                if ( holder.setThrowable(t) ) {
                    logger.error("策略组 "+group.getId()+" 运行策略 "+holder.getId()+" 失败: "+t.toString(), t);
                }
            }
        }
        group.onNoopSecond();
    }

    /**
     * 更新TradletGroup配置
     */
    private void processReloadGroup(TradletGroupTemplate template) {
        try{
            group.reload(template);
            List<Exchangeable> updatedInstruments = group.getUpdatedInstruments();
            if ( !updatedInstruments.isEmpty()) {
                TAService taService = beansContainer.getBean(TAService.class);
                taService.registerListener(updatedInstruments, group.getPriceLevels(), new TAListener() {
                    @Override
                    public void onNewBar(Exchangeable e, LeveledTimeSeries series) {
                        queueEvent(TradletEvent.EVENT_TYPE_MD_BAR, series);
                    }
                });
            }
        }catch(Throwable t) {
            logger.error("策略组 "+group.getId()+" 更新配置失败: "+t.toString(), t);
        }
    }

}
