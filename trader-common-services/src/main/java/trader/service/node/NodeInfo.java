package trader.service.node;

import java.util.Collections;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import trader.common.util.JsonEnabled;
import trader.common.util.JsonUtil;

public class NodeInfo implements JsonEnabled {
    private String id;
    private Map<String, Object> props;
    private NodeSession session;
    private long lastConnTime;
    private long lastDisconnTime;

    NodeInfo(String id){
        this.id = id;
    }

    public String getId() {
        return id;
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

    void setProps(Map<String, Object> props) {
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
        json.addProperty("id", id);
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
