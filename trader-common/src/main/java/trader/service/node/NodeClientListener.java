package trader.service.node;

import trader.service.node.NodeConstants.NodeState;

public interface NodeClientListener {

    public void onStateChanged(NodeEndpoint endpoint, NodeState oldState);

    /**
     * 处理更多消息
     */
    public NodeMessage onMessage(NodeMessage req);

}
