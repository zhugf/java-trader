package trader.service.node;

import java.util.Collection;
import java.util.Map;

/**
 * 节点管理服务, 用于通过WebSocket方式接入交易服务
 */
public interface NodeSessionService {

    public NodeSession getNodeSession(String nodeId);

    /**
     * 返回节点列表
     */
    public Collection<NodeSession> getNodeSessions();

    /**
     * 广播消息
     */
    public void topicPub(String topic, Map<String, Object> topicData);
}
