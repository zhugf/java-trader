package trader.service.node;

import java.util.List;
import java.util.Map;

/**
 * 节点客户端服务, 用于主动通过websocket连接节点管理服务
 */
public interface NodeClientChannel extends NodeEndpoint, NodeConstants {

    public void addListener(NodeClientListener listener);

    /**
     * 当前状态
     */
    public NodeState getState();

}
