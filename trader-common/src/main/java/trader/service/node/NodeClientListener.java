package trader.service.node;

import trader.service.node.NodeConstants.NodeState;

public interface NodeClientListener extends NodeTopicListener {

    public void onStateChanged(NodeState oldState);

}
