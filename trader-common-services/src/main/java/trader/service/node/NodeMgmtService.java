package trader.service.node;

import java.util.Collection;

/**
 * 节点管理服务, 用于通过WebSocket方式接入交易服务
 */
public interface NodeMgmtService {

    public static final String URI_WS_NODEMGMT = "/ws/nodeMgmt";

    public NodeInfo getNode(String nodeId);

    /**
     * 返回节点列表
     */
    public Collection<NodeInfo> getNodes(boolean activeOnly);

}
