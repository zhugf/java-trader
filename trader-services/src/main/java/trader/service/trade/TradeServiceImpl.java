package trader.service.trade;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import trader.service.ServiceConstants.ConnState;

/**
 * 交易事件服务代码, 并发送通知给相应的的AccountView
 */
@Service
public class TradeServiceImpl implements TradeService {
    private final static Logger logger = LoggerFactory.getLogger(TradeServiceImpl.class);

    static final String ITEM_ACCOUNT = "/TradeService/account";
    private static final String ITEM_ACCOUNTS = ITEM_ACCOUNT+"[]";

    @Autowired
    private ScheduledExecutorService scheduledExecutorService;

    @Autowired
    private ExecutorService executorService;

    @Autowired
    private BeansContainer beansContainer;

    private ServiceState state = ServiceState.Unknown;

    private Map<String, AccountImpl> accounts = new HashMap<>();

    private AccountImpl primaryAccount = null;

    @Override
    public void init(BeansContainer beansContainer) {
        state = ServiceState.Starting;
        reloadAccounts();
        scheduledExecutorService.scheduleAtFixedRate(()->{
            Map<String, AccountImpl> newOrUpdatedAccounts = reloadAccounts();
            connectTxnSessions(newOrUpdatedAccounts);
        }, 15, 15, TimeUnit.SECONDS);
    }

    @Override
    @PreDestroy
    public void destroy() {
        state = ServiceState.Stopped;
        for(AccountImpl account:accounts.values()) {
            account.destroy();
        }
    }

    /**
     * 启动后, 连接交易账户
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady(){
        state = ServiceState.Ready;
        executorService.execute(()->{
            connectTxnSessions(accounts);
        });
    }

    @Override
    public Account getPrimaryAccount() {
        return primaryAccount;
    }

    @Override
    public Account getAccount(String id) {
        return accounts.get(id);
    }

    @Override
    public Collection<Account> getAccounts() {
        return Collections.unmodifiableCollection(accounts.values());
    }

    /**
     * 当Account的连接状态发生变化时被回调
     * @param lastState
     */
    protected void onTxnSessionStateChanged(AccountImpl account, ConnState lastState) {
        ConnState state = account.getSession().getState();
        switch(state) {
        case Connected:
            //异步初始化账户
            executorService.execute(()->{
                account.init(beansContainer);
            });
            break;
        case Disconnected:
        case ConnectFailed:
            account.changeState(AccountState.NotReady);
            break;
        default:
            break;
        }
    }

    /**
     * 只能增加和更新不能删除
     */
    private Map<String, AccountImpl> reloadAccounts() {
        long t0 = System.currentTimeMillis();
        var currAccounts = accounts;
        var newOrUpdatedAccounts = new HashMap<String, AccountImpl>();
        var allAccounts = new HashMap<String, AccountImpl>();
        var accountElems = (List<Map>)ConfigUtil.getObject(ITEM_ACCOUNTS);
        String firstAccountId = null;
        if ( accountElems!=null ) {
            for (Map accountElem:accountElems) {
                String id = ConversionUtil.toString(accountElem.get("id"));
                if ( firstAccountId==null ) {
                    firstAccountId = id;
                }
                var currAccount = currAccounts.get(id);
                try{
                    if ( null==currAccount ) {
                        currAccount = createAccount(accountElem);
                        newOrUpdatedAccounts.put(currAccount.getId(), currAccount);
                    } else {
                        if ( currAccount.update(accountElem) ) {
                            newOrUpdatedAccounts.put(currAccount.getId(), currAccount);
                        }
                    }
                    allAccounts.put(currAccount.getId(), currAccount);
                }catch(Throwable t) {
                    logger.error("Create or update account failed from config: "+accountElem);
                }
            }
        }
        this.accounts = allAccounts;
        if ( primaryAccount==null ) {
            primaryAccount = accounts.get(firstAccountId);
        }
        long t1 = System.currentTimeMillis();
        String message = "Total "+allAccounts.size()+" accounts loaded in "+(t1-t0)+" ms, updated accounts: "+newOrUpdatedAccounts.keySet();
        if ( newOrUpdatedAccounts.size()>0 ) {
            logger.info(message);
        }else {
            if ( logger.isDebugEnabled() ) {
                logger.debug(message);
            }
        }
        return newOrUpdatedAccounts;
    }

    private AccountImpl createAccount(Map accountElem)
    {
        AccountImpl account = new AccountImpl(this, beansContainer, accountElem);
        return account;
    }

    /**
     * 启动完毕后, 连接交易通道
     */
    private void connectTxnSessions(Map<String, AccountImpl> accountsToConnect) {
        for(AccountImpl account:accountsToConnect.values()) {
            AbsTxnSession txnSession = (AbsTxnSession)account.getSession();
            switch(txnSession.getState()) {
            case Initialized:
            case ConnectFailed:
                txnSession.connect();
            break;
            }
        }
    }

}
