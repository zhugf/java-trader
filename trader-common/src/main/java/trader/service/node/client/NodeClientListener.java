package trader.service.node.client;

import java.util.Map;

import trader.service.node.NodeConstants.NodeState;

public interface NodeClientListener {

    public void onStateChanged(NodeClient client, NodeState oldState);

    public void onTopicPub(String topic, Map<String,Object> topicData);
}
