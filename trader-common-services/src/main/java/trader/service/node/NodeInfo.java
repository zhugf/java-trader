package trader.service.node;

import java.util.Collections;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import trader.common.util.JsonEnabled;
import trader.common.util.JsonUtil;
import trader.common.util.StringUtil;
import trader.service.node.NodeMessage.NodeType;

public class NodeInfo implements JsonEnabled {
    private String consistentId;
    private String id;
    private NodeType type;
    private Map<String, Object> props;
    private NodeSession session;
    private long lastConnTime;
    private long lastDisconnTime;

    NodeInfo(String consistentId, String id, NodeType type){
        this.consistentId = consistentId;
        this.id = id;
        this.type = type;
    }

    public String getConsistentId() {
        return consistentId;
    }

    public String getId() {
        return id;
    }

    public NodeType getType() {
        return type;
    }

    public boolean isActive() {
        return session!=null;
    }

    public Map<String, Object> getProps(){
        if ( props!=null ) {
            return Collections.unmodifiableMap(props);
        }else {
            return Collections.emptyMap();
        }
    }

    public NodeSession getSession() {
        return session;
    }

    public void setProps(Map<String, Object> props) {
        this.props = props;
    }

    void setSession(NodeSession session) {
        this.session = session;
        if ( session!=null ) {
            lastConnTime = System.currentTimeMillis();
        }else {
            lastDisconnTime = System.currentTimeMillis();
        }
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        if ( !StringUtil.isEmpty(consistentId)) {
            json.addProperty("consistentId", consistentId);
        }
        json.addProperty("id", id);
        json.addProperty("type", type.name());
        if ( props!=null ) {
            json.add("props", JsonUtil.object2json(props));
        }
        json.addProperty("lastConnTime", lastConnTime);
        json.addProperty("lastDisconnTime", lastDisconnTime);
        if ( session!=null ) {
            json.add("session", JsonUtil.object2json(session));
        }
        return json;
    }

}
