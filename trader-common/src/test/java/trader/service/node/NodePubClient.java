package trader.service.node;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import trader.service.node.NodeConstants.NodeState;
import trader.service.node.client.NodeClient;
import trader.service.node.client.NodeClientListener;

public class NodePubClient implements NodeClientListener {

    public static void main(String[] args) throws Exception
    {
        NodePubClient subClient = new NodePubClient();
        NodeClient client = new NodeClient("pub-"+System.currentTimeMillis(), subClient);
        client.connect(new URI("ws://localhost:10080"+NodeConstants.URI_WS_NODE), "api-ws", "#3Efc6YhN!");
        Thread.sleep(10*1000);

    }

    @Override
    public void onStateChanged(NodeClient client, NodeState oldState) {
        System.out.println("Node pub client to "+client.getURI()+" change state to "+client.getState());
        if ( client.getState()==NodeState.Ready ) {
            try{
                Map<String, Object> topicData = new HashMap<>();
                topicData.put("topicData", "TEST_DATATATA");
                client.topicPub("topic1", topicData);
            }catch(Throwable t) {
                t.printStackTrace(System.out);
            }
        }
    }

    @Override
    public void onTopicPub(String topic, Map<String, Object> topicData) {
        // TODO Auto-generated method stub

    }

}
