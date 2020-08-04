package trader.service.node;

import java.net.URI;
import java.util.Arrays;
import java.util.Map;

import trader.common.util.JsonUtil;
import trader.service.node.NodeConstants.NodeState;
import trader.service.node.client.NodeClient;
import trader.service.node.client.NodeClientListener;

public class NodeSubClient implements NodeClientListener {

    public static void main(String[] args) throws Exception
    {
        NodeSubClient subClient = new NodeSubClient();
        NodeClient client = new NodeClient("sub-"+System.currentTimeMillis(), subClient);
        client.connect(new URI("ws://localhost:10080"+NodeConstants.URI_WS_NODE), "api-ws", "#3Efc6YhN!");
        Thread.sleep(10*1000);

    }

    @Override
    public void onStateChanged(NodeClient client, NodeState oldState) {
        System.out.println("Node sub client to "+client.getURI()+" change state to "+client.getState());
        if ( client.getState()==NodeState.Ready ) {
            try{
                client.topicSub(Arrays.asList(new String[] {"topic1", "topic2"}));
            }catch(Throwable t) {
                t.printStackTrace(System.out);
            }
        }
    }

    @Override
    public void onTopicPub(String topic, Map<String, Object> topicData) {
        System.out.println("Node sub client get topic "+topic+" message: "+JsonUtil.object2json(topicData));
    }

}
