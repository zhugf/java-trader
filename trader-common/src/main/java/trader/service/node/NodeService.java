package trader.service.node;

import java.util.Collection;
import java.util.Map;

/**
 * (TraderBroker端)节点管理服务, 用于通过WebSocket方式接入交易服务.
 */
public interface NodeService extends NodeEndpoint{

    public NodeSession getSession(String nodeId);

    /**
     * 返回节点列表
     */
    public Collection<NodeSession> getNodeSessions();

}
