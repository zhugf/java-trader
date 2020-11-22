package trader.service.node;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import trader.common.util.JsonUtil;
import trader.service.ServiceConstants.AccountState;
import trader.service.ServiceConstants.ServiceState;
import trader.service.node.NodeConstants.NodeState;
import trader.service.trade.Account;
import trader.service.trade.AccountListener;
import trader.service.trade.Order;
import trader.service.trade.OrderStateTuple;
import trader.service.trade.TradeConstants;
import trader.service.trade.TradeConstants.AccMoney;
import trader.service.trade.TradeService;
import trader.service.trade.Transaction;
import trader.service.tradlet.Playbook;
import trader.service.tradlet.PlaybookStateTuple;
import trader.service.tradlet.TradletConstants.TradletGroupState;
import trader.service.tradlet.TradletGroup;
import trader.service.tradlet.TradletService;
import trader.service.tradlet.TradletServiceListener;

/**
 * 集群事件适配代码
 */
@Service
public class NodeEventAdapter {
    private static final Logger logger = LoggerFactory.getLogger(NodeEventAdapter.class);

    private static final int MAX_ACCOUNT_COUNT = 256;

    @Autowired
    private NodeClientChannel clientChannel;

    @Autowired
    private TradeService tradeService;

    @Autowired
    private TradletService tradletService;

    @Autowired
    private ExecutorService executorService;

    @Autowired
    private ScheduledExecutorService schduledExecutorService;

    private ServiceState state = ServiceState.Disabled;

    private long[] accountBalances = new long[MAX_ACCOUNT_COUNT];

    private int accountMoneyPubInterval = 3;

    @PostConstruct
    public void init() {
        //关联到NodeClient
        clientChannel.addListener(new NodeClientListener() {
            @Override
            public void onStateChanged(NodeEndpoint endpoint, NodeState oldState) {
            }
            @Override
            public NodeMessage onMessage(NodeMessage req) {
                return null;
            }
        });
        state = ServiceState.Starting;
    }

    @PreDestroy
    public void destroy(){
        state = ServiceState.Stopped;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onAppReady() {
        //注册策略的交易剧本通知
        tradletService.addListener(new TradletServiceListener() {
            @Override
            public void onGroupStateChanged(TradletGroup group, TradletGroupState oldState) {
            }
            @Override
            public void onPlaybookStateChanged(TradletGroup group, Playbook pb, PlaybookStateTuple oldStateTuple) {
                if ( canTopicPub() ) {
                    pubPlaybook(group, pb);
                }
            }
        });
        //注册账户报单/成交回调
        for(Account account:tradeService.getAccounts()) {
            account.addAccountListener(new AccountListener() {
                @Override
                public void onAccountStateChanged(Account account, AccountState oldState) {
                    accountBalances = new long[MAX_ACCOUNT_COUNT];
                }
                @Override
                public void onTransaction(Account account, Order order, Transaction txn) {
                    if ( canTopicPub() ) {
                        pubAccountTxn(account, order, txn);
                    }
                }
                @Override
                public void onOrderStateChanged(Account account, Order order, OrderStateTuple lastStateTuple) {
                    if ( canTopicPub() ) {
                        pubAccountOrder(account, order);
                    }
                }
            });
        }
        //定期检查, 发送账户的资金变化
        schduledExecutorService.scheduleAtFixedRate(()->{
            if ( canTopicPub() ) {
                pubAccountMoneyChanged();
            }
        }, accountMoneyPubInterval, accountMoneyPubInterval, TimeUnit.SECONDS);
        state = ServiceState.Ready;
    }

    private boolean canTopicPub() {
        return ServiceState.Ready==state && clientChannel.getState()==NodeState.Ready;
    }

    private void pubAccountMoneyChanged() {
        //判断是否资金发生变动
        long[] currAccountBalances = new long[MAX_ACCOUNT_COUNT];
        List<Account> accounts = tradeService.getAccounts();
        boolean same=true;
        for(int i=0;i<accounts.size();i++) {
            currAccountBalances[i] = accounts.get(i).getMoney(AccMoney.Balance);
            if ( currAccountBalances[i]!=accountBalances[i]) {
                same = false;
            }
        }
        if ( same ) { //没有变动不发更新
            return;
        }
        Map<String, Object> topicData = new HashMap<>();
        JsonArray accountsData = new JsonArray();
        for(Account account:accounts) {
            AccountState accState = account.getState();
            JsonObject accountInfo = new JsonObject();
            accountInfo.addProperty("id", account.getId());
            accountInfo.addProperty("state", accState.name());
            if ( AccountState.Ready==accState ) {
                accountInfo.add("money", TradeConstants.accMoney2json(account.getMoneys()));
            }
            accountsData.add(accountInfo);
        }
        topicData.put("accounts", accountsData);
        try{
            clientChannel.topicPub(NodeConstants.TOPIC_TRADE_ACCOUNT_MONEY, topicData);
            accountBalances = currAccountBalances;
        }catch(Throwable t) {
            logger.error("Publish account money failed", t);
        }
    }

    private void pubAccountOrder(Account account, Order order) {
        Map<String, Object> topicData = new HashMap<>();
        topicData.put("accountId", account.getId());
        topicData.put("order", JsonUtil.object2json(order));
        try{
            clientChannel.topicPub(NodeConstants.TOPIC_TRADE_ORDER, topicData);
        }catch(Throwable t) {
            logger.error("Publish order failed", t);
        }
    }

    private void pubAccountTxn(Account account, Order order, Transaction txn) {
        Map<String, Object> topicData = new HashMap<>();
        topicData.put("accountId", account.getId());
        topicData.put("orderId", order.getId());
        topicData.put("txn", JsonUtil.object2json(txn));
        try{
            clientChannel.topicPub(NodeConstants.TOPIC_TRADE_TXN, topicData);
        }catch(Throwable t) {
            logger.error("Publish txn failed", t);
        }
    }

    private void pubPlaybook(TradletGroup group, Playbook pb) {
        Map<String, Object> topicData = new HashMap<>();
        topicData.put("groupId", group.getId());
        topicData.put("pb", JsonUtil.object2json(pb));
        try{
            clientChannel.topicPub(NodeConstants.TOPIC_TRADLET_PLAYBOOK, topicData);
        }catch(Throwable t) {
            logger.error("Publish playbook failed", t);
        }
    }
}
