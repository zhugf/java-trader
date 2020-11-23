package trader.service.trade;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import trader.common.beans.BeansContainer;
import trader.common.beans.ServiceState;
import trader.common.config.ConfigUtil;
import trader.common.util.ConversionUtil;
import trader.service.ServiceConstants.AccountState;
import trader.service.event.AsyncEvent;
import trader.service.event.AsyncEventFilter;
import trader.service.event.AsyncEventService;
import trader.service.md.MarketData;
import trader.service.md.MarketDataService;
import trader.service.plugin.Plugin;
import trader.service.plugin.PluginService;
import trader.service.trade.ctp.CtpTxnSessionFactory;
import trader.service.trade.spi.AbsTxnSession;

/**
 * 交易事件服务代码, 并发送通知给相应的的AccountView.
 * <BR>所有与交易相关事件: 报单, 报单回报, 成交回报等等, 统一使用异步消息机制, 在独立的事件处理线程中执行.
 */
@Service
public class TradeServiceImpl implements TradeService, AsyncEventFilter {
    private final static Logger logger = LoggerFactory.getLogger(TradeServiceImpl.class);

    static final String ITEM_ACCOUNT = "/TradeService/account";
    static final String ITEM_ACCOUNTS = ITEM_ACCOUNT+"[]";

    @Autowired
    private ScheduledExecutorService scheduledExecutorService;

    @Autowired
    private MarketTimeService mtService;

    @Autowired
    private MarketDataService mdService;

    @Autowired
    private ExecutorService executorService;

    @Autowired
    private AsyncEventService asyncEventService;

    @Autowired
    private BeansContainer beansContainer;

    private List<TradeServiceListener> listeners = new ArrayList<>();

    private Map<String, TxnSessionFactory> txnSessionFactories = new HashMap<>();

    private ServiceState state = ServiceState.Unknown;

    private OrderRefGenImpl orderRefGen;

    private List<AccountImpl> accounts = new ArrayList<>();

    private Map<String, AccountImpl> accountByIds = new HashMap<>();

    private AccountImpl primaryAccount = null;

    @Override
    public void init(BeansContainer beansContainer) {
        state = ServiceState.Starting;
        orderRefGen = new OrderRefGenImpl(this, mtService.getTradingDay(), beansContainer);
        //接收行情, 异步更新账户的持仓盈亏
        mdService.addListener((MarketData md)->{
            accountOnMarketData(md);
        });
        //接收交易事件, 在单一线程中处理
        asyncEventService.addFilter(AsyncEventService.FILTER_CHAIN_MAIN, this, AsyncEvent.EVENT_TYPE_PROCESSOR_MASK);

        //自动发现交易接口API
        txnSessionFactories = discoverTxnSessionProviders(beansContainer);
        reloadAccounts();

        scheduledExecutorService.scheduleAtFixedRate(()->{
            try{
                List<AccountImpl> newOrUpdatedAccounts = reloadAccounts();
                connectTxnSessions(newOrUpdatedAccounts);
            }catch(Throwable t) {
                logger.error("Reload accounts faild", t);
            }
        }, 15, 15, TimeUnit.SECONDS);
    }

    @Override
    @PreDestroy
    public void destroy() {
        state = ServiceState.Stopped;
        for(AccountImpl account:accounts) {
            account.destroy();
        }
    }

    /**
     * 启动完成后, 连接交易账户
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady(){
        state = ServiceState.Ready;
        executorService.execute(()->{
            connectTxnSessions(accounts);
        });
    }

    public TradeServiceType getType() {
        return TradeServiceType.RealTime;
    }

    public OrderRefGen getOrderRefGen() {
        return orderRefGen;
    }

    @Override
    public Account getPrimaryAccount() {
        return primaryAccount;
    }

    @Override
    public Account getAccount(String id) {
        return accountByIds.get(id);
    }

    @Override
    public List<Account> getAccounts() {
        return (List)(accounts);
    }

    @Override
    public Map<String, TxnSessionFactory> getTxnSessionFactories(){
        return Collections.unmodifiableMap(txnSessionFactories);
    }

    public void addListener(TradeServiceListener listener) {
        if ( !listeners.contains(listener) ) {
            listeners.add(listener);
        }
    }

    /**
     * 处理所有的交易相关的事件
     */
    @Override
    public boolean onEvent(AsyncEvent event) {
        try{
            event.processor.process(event.eventType, event.data, event.data2);
        }catch(Throwable t) {
            logger.error("Async event process failed on data "+event.data);
        }
        return true;
    }

    /**
     * 只能增加和更新不能删除
     */
    private List<AccountImpl> reloadAccounts() {
        long t0 = System.currentTimeMillis();
        List<AccountImpl> currAccounts = accounts;
        Map<String, AccountImpl> currAccountByIds = new HashMap<>();
        for(AccountImpl account:currAccounts) {
            currAccountByIds.put(account.getId(), account);
        }
        List<AccountImpl> newAccounts = new ArrayList<>();
        List<String> newAccountIds = new ArrayList<>();
        String primaryAccountId = null;
        List<AccountImpl> updatedAccounts = new ArrayList<>();
        List<String> updatedAccountIds = new ArrayList<>();
        List<AccountImpl> allAccounts = new ArrayList<>();
        AccountImpl currPrimaryAccount = null;
        List<Map> accountElems = (List<Map>)ConfigUtil.getObject(ITEM_ACCOUNTS);
        if ( accountElems!=null ) {
            for (Map accountElem:accountElems) {
                String id = ConversionUtil.toString(accountElem.get("id"));
                boolean primary = ConversionUtil.toBoolean(accountElem.get("primary"), false);
                boolean disabled = ConversionUtil.toBoolean(accountElem.get("disabled"), false);
                if ( disabled ) {
                    continue;
                }
                AccountImpl currAccount = currAccountByIds.get(id);
                try{
                    if ( null==currAccount ) {
                        currAccount = createAccount(accountElem);
                        newAccounts.add(currAccount);
                        newAccountIds.add(currAccount.getId());
                    } else {
                        if ( currAccount.update(accountElem) ) {
                            updatedAccounts.add(currAccount);
                            updatedAccountIds.add(currAccount.getId());
                        }
                    }
                    allAccounts.add(currAccount);
                    if ( primary ) {
                        currPrimaryAccount = currAccount;
                    }
                }catch(Throwable t) {
                    logger.error("Create or update account failed from config: "+accountElem);
                }
            }
        }
        this.accounts = allAccounts;
        Map<String, AccountImpl> accountByIds = new HashMap<>();
        for(AccountImpl account:allAccounts) {
            accountByIds.put(account.getId(), account);
        }
        this.accountByIds = accountByIds;
        if ( currPrimaryAccount!=null ) {
            primaryAccount = currPrimaryAccount;
        }
        if ( primaryAccount==null && !accounts.isEmpty()) {
            primaryAccount = accounts.get(0);
        }
        long t1 = System.currentTimeMillis();
        String message = "Total "+allAccounts.size()+" accounts loaded in "+(t1-t0)+" ms, new: "+newAccountIds+" updated: "+updatedAccountIds;
        if ( updatedAccounts.size()>0 ) {
            logger.info(message);
        }else {
            if ( logger.isDebugEnabled() ) {
                logger.debug(message);
            }
        }
        for(AccountImpl account:newAccounts) {
            account.restoreFromRepository();
        }
        return updatedAccounts;
    }

    private AccountImpl createAccount(Map accountElem)
    {
        AccountImpl account = new AccountImpl(this, beansContainer, accountElem);
        account.addAccountListener(new AccountListener() {
            @Override
            public void onTransaction(Account account, Order order, Transaction transaction) {
            }
            @Override
            public void onOrderStateChanged(Account account, Order order, OrderStateTuple lastStateTuple) {
            }
            @Override
            public void onAccountStateChanged(Account account, AccountState oldState) {
                if ( ServiceState.Ready==state ) {
                    executorService.execute(()->{
                        notifyAccountStateChanged(account, oldState);
                    });
                }
            }
        });
        return account;
    }

    /**
     * 启动完毕后, 连接交易通道
     */
    private void connectTxnSessions(List<AccountImpl> accountsToConnect) {
        for(AccountImpl account:accountsToConnect) {
            AbsTxnSession txnSession = (AbsTxnSession)account.getSession();
            switch(txnSession.getState()) {
            case Initialized:
            case ConnectFailed:
                txnSession.connect(account.getConnectionProps());
                break;
            default:
                break;
            }
        }
    }

    /**
     * 重新计算持仓利润
     */
    private void accountOnMarketData(MarketData md) {
        for(int i=0; i<accounts.size();i++) {
            try{
                accounts.get(i).onMarketData(md);
            }catch(Throwable t) {
                logger.error("Async market event process failed on data "+md);
            }
        }
    }

    private void notifyAccountStateChanged(Account account, AccountState oldState) {
        for(TradeServiceListener l:listeners) {
            try{
                l.onAccountStateChanged(account, oldState);
            }catch(Throwable t) {}
        }
    }

    public static Map<String, TxnSessionFactory> discoverTxnSessionProviders(BeansContainer beansContainer ){
        Map<String, TxnSessionFactory> result = new TreeMap<>();
        result.put(TxnSession.PROVIDER_CTP, new CtpTxnSessionFactory());
        PluginService pluginService = beansContainer.getBean(PluginService.class);
        if (pluginService!=null) {
            for(Plugin plugin : pluginService.search(Plugin.PROP_EXPOSED_INTERFACES + "=" + TxnSessionFactory.class.getName())) {
                Map<String, TxnSessionFactory> pluginProducerFactories = plugin.getBeansOfType(TxnSessionFactory.class);
                result.putAll(pluginProducerFactories);
            }
        }
        return result;
    }

}
