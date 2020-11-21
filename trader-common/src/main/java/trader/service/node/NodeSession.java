package trader.service.node;

import java.util.Collection;
import java.util.Map;

/**
 * 代表一个活动WebSocket会话
 */
public interface NodeSession extends NodeConstants {

    public String getId();

    public String getConsistentId();

    public NodeType getType();

    public NodeState getState();

    public Map<String, Object> getAttrs();

    public Collection<String> getTopics();
}
