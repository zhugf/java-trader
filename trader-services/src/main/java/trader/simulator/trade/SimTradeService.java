package trader.simulator.trade;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import trader.common.beans.BeansContainer;
import trader.common.config.ConfigUtil;
import trader.common.util.ConversionUtil;
import trader.common.util.TimestampSeqGen;
import trader.service.md.MarketData;
import trader.service.md.MarketDataService;
import trader.service.trade.Account;
import trader.service.trade.AccountImpl;
import trader.service.trade.MarketTimeService;
import trader.service.trade.OrderRefGen;
import trader.service.trade.OrderRefGenImpl;
import trader.service.trade.TradeService;
import trader.service.trade.TradeServiceListener;
import trader.service.trade.TxnSession;
import trader.service.trade.TxnSessionFactory;
import trader.service.trade.spi.AbsTxnSession;

/**
 * 模拟成交服务
 */
public class SimTradeService implements TradeService {
    private final static Logger logger = LoggerFactory.getLogger(SimTradeService.class);

    static final String ITEM_ACCOUNT = "/TradeService/account";
    static final String ITEM_ACCOUNTS = ITEM_ACCOUNT+"[]";

    private BeansContainer beansContainer;

    private Map<String, TxnSessionFactory> txnSessionFactories = new HashMap<>();

    private TimestampSeqGen orderIdGen;

    private OrderRefGen orderRefGen;

    private List<AccountImpl> accounts = new ArrayList<>();

    private AccountImpl primaryAccount = null;

    @Override
    public void init(BeansContainer beansContainer) throws Exception {
        this.beansContainer = beansContainer;
        MarketTimeService mtService = beansContainer.getBean(MarketTimeService.class);
        if ( orderRefGen==null ) {
            orderRefGen = new OrderRefGenImpl(this, mtService.getTradingDay(), beansContainer);
        }
        orderIdGen = new TimestampSeqGen(mtService);
        MarketDataService mdService = beansContainer.getBean(MarketDataService.class);
        //接收行情, 异步更新账户的持仓盈亏
        mdService.addListener((MarketData md)->{
            accountOnMarketData(md);
        });
        //自动发现交易接口API
        txnSessionFactories = discoverTxnSessionProviders(beansContainer);
        loadAccounts();
        connectTxnSessions(accounts);
    }

    @Override
    public void destroy() {
        for(AccountImpl account:accounts) {
            account.destroy();
        }
    }

    public TradeServiceType getType() {
        return TradeServiceType.Simulator;
    }

    @Override
    public Map<String, TxnSessionFactory> getTxnSessionFactories(){
        return Collections.unmodifiableMap(txnSessionFactories);
    }

    public OrderRefGen getOrderRefGen() {
        return orderRefGen;
    }

    public TimestampSeqGen getOrderIdGen() {
        return orderIdGen;
    }

    @Override
    public Account getPrimaryAccount() {
        return primaryAccount;
    }

    @Override
    public Account getAccount(String id) {
        for(AccountImpl account:accounts) {
            if ( account.getId().equals(id)) {
                return account;
            }
        }
        return null;
    }

    @Override
    public Collection<Account> getAccounts() {
        return Collections.unmodifiableCollection(accounts);
    }

    public void addListener(TradeServiceListener listener) {
        //do nothing
    }

    public void setOrderRefMgr(OrderRefGen orderRefGen) {
        this.orderRefGen = orderRefGen;
    }

    private void loadAccounts() {
        List<Map> accountElems = (List<Map>)ConfigUtil.getObject(ITEM_ACCOUNTS);
        List<AccountImpl> allAccounts = new ArrayList<>();
        if ( accountElems!=null ) {
            for (Map accountElem:accountElems) {
                accountElem.put("provider", TxnSession.PROVIDER_SIM);
                String id = ConversionUtil.toString(accountElem.get("id"));
                AccountImpl currAccount = createAccount(accountElem);
                allAccounts.add(currAccount);
            }
        }
        this.accounts = allAccounts;
        if ( primaryAccount==null && !accounts.isEmpty()) {
            primaryAccount = accounts.get(0);
        }
    }

    /**
     * 启动完毕后, 连接交易通道
     */
    private void connectTxnSessions(List<AccountImpl> accountsToConnect) {
        for(AccountImpl account:accountsToConnect) {
            AbsTxnSession txnSession = (AbsTxnSession)account.getSession();
            txnSession.connect(account.getConnectionProps());
        }
    }

    private static Map<String, TxnSessionFactory> discoverTxnSessionProviders(BeansContainer beansContainer ){
        Map<String, TxnSessionFactory> result = new TreeMap<>();
        result.put(TxnSession.PROVIDER_SIM, new SimTxnSessionFactory());
        return result;
    }

    private AccountImpl createAccount(Map accountElem)
    {
        AccountImpl account = new AccountImpl(this, beansContainer, accountElem);
        return account;
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

}
