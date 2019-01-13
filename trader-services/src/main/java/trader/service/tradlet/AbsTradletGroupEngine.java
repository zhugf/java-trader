package trader.service.tradlet;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import trader.common.beans.BeansContainer;
import trader.common.beans.Lifecycle;
import trader.service.ServiceConstants.AccountState;
import trader.service.md.MarketData;
import trader.service.ta.LeveledTimeSeries;
import trader.service.trade.Account;
import trader.service.trade.AccountListener;
import trader.service.trade.Order;
import trader.service.trade.OrderStateTuple;
import trader.service.trade.Transaction;

/**
 * TradletGroupEngine公共类
 */
public abstract class AbsTradletGroupEngine implements Lifecycle, AccountListener {
    private static final Logger logger = LoggerFactory.getLogger(AbsTradletGroupEngine.class);

    protected TradletService tradletService;
    protected BeansContainer beansContainer;
    protected TradletGroupImpl group;

    public TradletGroupImpl getGroup() {
        return group;
    }

    @Override
    public void init(BeansContainer beansContainer) {
        this.beansContainer = beansContainer;
        this.tradletService = beansContainer.getBean(TradletServiceImpl.class);

        //关联TradletGroup到Account
        group.setState(TradletGroup.State.Enabled);
        group.getAccount().addAccountListener(this);
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

    /**
     * 排队处理TradletGroup事件
     */
    public abstract void queueEvent(int eventType, Object data);

    protected void processEvent(int eventType, Object data) throws Exception {
        switch(eventType) {
        case TradletEvent.EVENT_TYPE_MD_TICK:
            processTick((MarketData)data);
            break;
        case TradletEvent.EVENT_TYPE_MD_BAR:
            processBar((LeveledTimeSeries)data);
            break;
        case TradletEvent.EVENT_TYPE_MISC_GROUP_UPDATE:
            processUpdateGroup((TradletGroupTemplate)data);
            break;
        case TradletEvent.EVENT_TYPE_TRADE_ORDER:
            processOrder((Order)data);
            break;
        case TradletEvent.EVENT_TYPE_TRADE_TXN:
            processTransaction((Transaction)data);
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

    }


    /**
     * 更新TradletGroup配置
     */
    private void processUpdateGroup(TradletGroupTemplate template) {
        try{
            group.update(template);
        }catch(Throwable t) {
            logger.error("策略组 "+group.getId()+" 更新配置失败: "+t.toString(), t);
        }
    }

}
