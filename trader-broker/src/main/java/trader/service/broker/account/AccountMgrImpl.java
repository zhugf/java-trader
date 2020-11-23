package trader.service.broker.account;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import trader.service.node.NodeConstants;
import trader.service.node.NodeService;
import trader.service.node.NodeTopicListener;

/**
 * 账户集中展示实现类
 */
@Service
public class AccountMgrImpl implements NodeTopicListener {
    private static final Logger logger = LoggerFactory.getLogger(AccountMgrImpl.class);

    @Autowired
    private NodeService nodeService;

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

    //------------ NodeTopicListener -----------
    @Override
    public void onTopicPub(String topic, Map<String, Object> topicData) {
    }

}
