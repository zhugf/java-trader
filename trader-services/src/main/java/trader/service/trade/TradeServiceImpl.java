package trader.service.trade;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import trader.common.config.ConfigUtil;
import trader.common.util.ConversionUtil;
import trader.service.trade.TradeConstants.TxnProvider;
import trader.service.trade.ctp.CtpTxnSession;

/**
 * 交易事件服务代码, 并发送通知给相应的的AccountView
 */
@Service
public class TradeServiceImpl implements TradeService {
    private final static Logger logger = LoggerFactory.getLogger(TradeServiceImpl.class);

    private static final String ITEM_ACCOUNTS = "/TradeService/account[]";

    @Autowired
    private ScheduledExecutorService scheduledExecutorService;

    private Map<String, AccountImpl> accounts = new HashMap<>();
    private AccountImpl primaryAccount = null;

    @PostConstruct
    public void init() {
        reloadAccounts();
        scheduledExecutorService.scheduleAtFixedRate(()->{
            reloadAccounts();
        }, 15, 15, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void destroy() {

    }

    /**
     * 启动后, 连接交易账户
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady(){
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

    void onTxnSessionStatusChanged(AccountImpl account) {
    }

    /**
     * 只能增加和更新不能删除
     */
    private void reloadAccounts() {
        long t0 = System.currentTimeMillis();
        var currAccounts = accounts;
        var newAccountIds = new ArrayList<String>();
        var allAccounts = new HashMap<String, AccountImpl>();
        var accountElems = (List<Map>)ConfigUtil.getObject(ITEM_ACCOUNTS);
        String firstAccountId = null;
        for (Map accountElem:accountElems) {
            String id = ConversionUtil.toString(accountElem.get("id"));
            if ( firstAccountId==null ) {
                firstAccountId = id;
            }
            var currAccount = currAccounts.get(id);
            try{
                if ( null==currAccount ) {
                    currAccount = createAccount(accountElem);
                    newAccountIds.add(currAccount.getId());
                } else {
                    currAccount.update(accountElem);
                }
                updateAccountViews(currAccount);
                allAccounts.put(currAccount.getId(), currAccount);
            }catch(Throwable t) {
                logger.error("Create or update account failed from config: "+accountElem);
            }
        }

        this.accounts = allAccounts;
        if ( primaryAccount==null ) {
            primaryAccount = accounts.get(firstAccountId);
        }
        long t1 = System.currentTimeMillis();
        String message = "Total "+allAccounts.size()+" accounts loaded in "+(t1-t0)+" ms, new accounts: "+newAccountIds;
        if ( newAccountIds.size()>0 ) {
            logger.info(message);
        }else {
            if ( logger.isDebugEnabled() ) {
                logger.debug(message);
            }
        }
    }

    private AccountImpl createAccount(Map accountElem)
    {
        AccountImpl account = new AccountImpl(accountElem);
        TxnProvider provider = ConversionUtil.toEnum(TxnProvider.class, accountElem.get("txnProvider"));
        account.attachTxnSession(createTxnSession(account, provider));
        return account;
    }

    private AbsTxnSession createTxnSession(AccountImpl account, TxnProvider provider) {
        switch(provider) {
        case ctp:
            return new CtpTxnSession(this, account);
        default:
            throw new RuntimeException("Unsupported account txn provider: "+provider);
        }
    }

    private void updateAccountViews(AccountImpl account) {
        String path = ITEM_ACCOUNTS.substring(0, ITEM_ACCOUNTS.length()-2)+"#"+account.getId()+"/view[]";
        Map<String, AccountViewImpl> currViews = new HashMap<>((Map)account.getViews());
        Map<String, AccountViewImpl> allViews = new HashMap<>();
        var newViewIds = new ArrayList<String>();

        var viewElems = (List<Map>)ConfigUtil.getObject(path);
        for(Map viewElem: viewElems) {
            String id = ConversionUtil.toString(viewElem.get("id"));
            AccountViewImpl view = currViews.get(id);
            if ( view==null ) {
                view = new AccountViewImpl(account, viewElem);
                newViewIds.add(id);
            }else {
                view.update(viewElem);
            }
            allViews.put(id, view);
        }
        account.setViews(allViews);
        String message = "Account "+account.getId()+" load "+allViews.size()+" views, new/updated: "+newViewIds;
        if ( newViewIds.size()>0 ) {
            logger.info(message);
        }else {
            if ( logger.isDebugEnabled() ) {
                logger.debug(message);
            }
        }
    }

}
