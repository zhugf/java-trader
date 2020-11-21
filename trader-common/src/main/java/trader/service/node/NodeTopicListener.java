package trader.service.node;

import java.util.Map;

public interface NodeTopicListener {

    /**
     * 广播消息回调函数
     *
     * @param topic
     * @param topicData
     */
    public void onTopicPub(String topic, Map<String,Object> topicData);

}
