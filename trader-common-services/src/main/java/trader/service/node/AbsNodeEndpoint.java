package trader.service.node;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import trader.common.exception.AppException;
import trader.common.util.ConversionUtil;
import trader.service.node.AbsNodeEndpoint.ReqItem;
import trader.service.node.NodeConstants.NodeState;

public abstract class AbsNodeEndpoint implements NodeEndpoint {
    private static final Logger logger = LoggerFactory.getLogger(AbsNodeEndpoint.class);

    protected static class ReqItem{
       NodeMessage responseMsg;
    }

    protected Map<String, List<NodeTopicListener>> topicListeners = new HashMap<>();
    protected Map<Integer, ReqItem> pendingReqs = new ConcurrentHashMap<>();
    protected int defaultTimeout = 30*1000;

    /**
     * 本地注册Topic回调函数
     *
     * @param topics
     * @param listener
     *
     * @return 合并后的主题
     */
    protected String[] registerTopicListeners(String[] topics, NodeTopicListener listener) {
        for(String topic:topics) {
            List<NodeTopicListener> listeners = this.topicListeners.get(topic);
            if ( null==listeners) {
                listeners = new ArrayList<>();
                topicListeners.put(topic, listeners);
            }
            if ( !listeners.contains(listener)) {
                listeners.add(listener);
            }
        }
        List<String> mergedTopics = new ArrayList<>(topicListeners.keySet());
        return mergedTopics.toArray(new String[mergedTopics.size()]);
    }

    /**
     * 派发Topic消息
     */
    protected void doDispatchTopic(NodeMessage msg) {
        String topic = ConversionUtil.toString(msg.getField(NodeMessage.FIELD_TOPIC));
        List<NodeTopicListener> listeners = topicListeners.get(topic);
        if ( null!=listeners ) {
            Map<String, Object> topicData = new HashMap<>(msg.getFields());
            topicData.remove(NodeMessage.FIELD_TOPIC);
            for(NodeTopicListener topicListener:listeners) {
                try{
                    topicListener.onTopicPub(topic, topicData);
                }catch(Throwable t) {
                    logger.error("Topic listener "+topicListener+" process message failed: "+msg);
                }
            }
        }
    }

    protected boolean doResponseNotify(NodeMessage msg) {
        boolean found = false;
        if ( msg.getType().isResponse() ) {
            ReqItem item = pendingReqs.get(msg.getReqId());
            if ( item!=null ) {
                item.responseMsg = msg;
                synchronized(item) {
                    item.notify();
                }
                found = true;
            }
        }
        return found;
    }

}
