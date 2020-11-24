package trader.service.broker;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.google.gson.JsonObject;

import trader.common.util.ConversionUtil;
import trader.common.util.JsonUtil;
import trader.service.node.NodeConstants;
import trader.service.node.NodeService;
import trader.service.node.NodeTopicListener;

/**
 * 账户集中展示实现类
 */
@Service
public class TradeViewMgrImpl implements TradeViewMgr, NodeTopicListener {
    private static final Logger logger = LoggerFactory.getLogger(TradeViewMgrImpl.class);

    @Autowired
    private NodeService nodeService;

    private Map<String, AccountViewImpl> accounts = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        try {
            nodeService.topicSub(new String[] {
                    NodeConstants.TOPIC_TRADE_ACCOUNT_INFO,
                    NodeConstants.TOPIC_TRADE_ACCOUNT_MONEY,
                    NodeConstants.TOPIC_TRADE_ORDER,
                    NodeConstants.TOPIC_TRADE_TXN
                    }, this);
        }catch(Throwable t) {
            logger.error("node service topic subscribe failed", t);
        }
    }

    @PreDestroy
    public void destroy() {
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onAppReady() {
    }

    public AccountView getAccount(String accId) {
        return accounts.get(accId);
    }

    public Map<String, AccountView> getAccounts(){
        return Collections.unmodifiableMap(accounts);
    }

    //------------ NodeTopicListener -----------
    @Override
    public void onTopicPub(String topic, Map<String, Object> topicData) {
        switch(topic){
            case NodeConstants.TOPIC_TRADE_ACCOUNT_INFO:
                onAccountInfo(topicData);
                break;
            case NodeConstants.TOPIC_TRADE_ACCOUNT_MONEY:
                onAccountMoney(topicData);
                break;
            case NodeConstants.TOPIC_TRADE_ORDER:
                onTradeOrder(topicData);
                break;
            case NodeConstants.TOPIC_TRADE_TXN:
                onTradeTxn(topicData);
                break;
        }
    }

    private void onAccountInfo(Map<String, Object> topicData) {
        List<Map> accounts = (List)topicData.get("accounts");
        for(Map accData:accounts) {
            String id = ConversionUtil.toString(accData.get("id"));
            AccountViewImpl accView = this.accounts.get(id);
            if ( null==accView ) {
                accView = new AccountViewImpl(accData);
                this.accounts.put(accView.getId(), accView);
            } else {
                accView.update(accData);
            }
        }
    }

    private void onAccountMoney(Map<String, Object> topicData) {
        List<Map> accounts = (List)topicData.get("accounts");
        for(Map accData:accounts) {
            String id = ConversionUtil.toString(accData.get("id"));
            AccountViewImpl accView = this.accounts.get(id);
            if ( null!=accView ) {
                accView.update(accData);
            }
        }
    }

    private void onTradeOrder(Map<String, Object> topicData) {
        String accountId =  ConversionUtil.toString(topicData.get("accountId"));
        Map orderData = (Map)topicData.remove("order");
        AccountViewImpl accView = this.accounts.get(accountId);
        if ( null!=accView ) {
            accView.updateOrder((JsonObject)JsonUtil.object2json(orderData));
        }
    }

    private void onTradeTxn(Map<String, Object> topicData) {

    }

}
