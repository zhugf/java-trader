package trader.service.node;

import java.util.Map;

import trader.common.exception.AppException;

public interface NodeEndpoint {

    /**
     * 广播消息
     */
    public void topicPub(String topic, Map<String, Object> topicData) throws AppException;

    /**
     * 订阅主题
     */
    public void topicSub(String[] topics, NodeTopicListener listener) throws AppException;
}
