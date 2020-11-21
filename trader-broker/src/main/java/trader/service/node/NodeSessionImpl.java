package trader.service.node;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpHeaders;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.adapter.NativeWebSocketSession;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import trader.common.exception.AppException;
import trader.common.util.ConversionUtil;
import trader.common.util.JsonEnabled;
import trader.common.util.JsonUtil;
import trader.common.util.StringUtil;
import trader.service.ServiceErrorConstants;

/**
 * 代表一个活跃的 Node WebSocket 连接
 */
public class NodeSessionImpl implements NodeSession, JsonEnabled {
    public static final String ATTR_SESSION = "nodeSession";

    private NodeServiceImpl nodeService;
    private final String id;
    private String consistentId;
    private NodeType type;
    private Set<String> topics = Collections.emptySet();
    private Map<String, Object> attrs;
    private volatile NodeState state = NodeState.NotConfigured;
    private long stateTime;
    private WebSocketSession wsSession;
    private String remoteAddr;
    private long creationTime;
    private volatile long lastRecvTime;
    private volatile long lastSentTime;

    private long totalMessagesSent;
    private long totalMessagesRecv;

    NodeSessionImpl(NodeServiceImpl nodeMgmtService, WebSocketSession wsSession){
        this.nodeService = nodeMgmtService;
        this.wsSession = wsSession;
        this.remoteAddr = wsSession.getRemoteAddress().getHostString();
        HttpHeaders headers = wsSession.getHandshakeHeaders();
        String realIp = headers.getFirst("X-Real-IP");
        if ( !StringUtil.isEmpty(realIp)) {
            remoteAddr = realIp;
        }
        String forwardFor = headers.getFirst("X-Forwarded-For");
        if ( !StringUtil.isEmpty(forwardFor)) {
            remoteAddr = forwardFor;
        }
        creationTime = System.currentTimeMillis();
        lastRecvTime = creationTime;
        lastSentTime = creationTime;
        changeState(NodeState.Initializing);
        this.type = NodeType.GenericClient;
        this.id = wsSession.getId();
    }

    public String getId() {
        return id;
    }

    public String getConsistentId() {
        return consistentId;
    }

    @Override
    public NodeType getType() {
        return type;
    }

    public NodeState getState() {
        return state;
    }

    void changeState(NodeState s) {
        if (this.state != s) {
            this.state = s;
            stateTime = System.currentTimeMillis();
        }
    }

    void setAttrs(Map<String,Object> attrs) {
        this.attrs = attrs;
    }

    @Override
    public Map<String, Object> getAttrs() {
        return attrs;
    }

    @Override
    public Collection<String> getTopics() {
        return topics;
    }

    void setTopics(Collection<String> topics) {
        if ( null==topics || topics.isEmpty() ) {
            this.topics = Collections.emptySet();
        } else {
            this.topics = new HashSet<>(topics);
        }
    }

    public boolean isTopicMatch(String topic) {
        return topics.contains(topic);
    }

    public String getRemoteAddress() {
        return remoteAddr;
    }

    public long getLastRecvTime() {
        return lastRecvTime;
    }

    public long getStateTime() {
        return stateTime;
    }

    public void init(NodeMessage initMessage) {
        consistentId = (String)initMessage.getField(NodeMessage.FIELD_NODE_CONSISTENT_ID);
        type = ConversionUtil.toEnum(NodeType.class, initMessage.getField(NodeMessage.FIELD_NODE_TYPE));
        attrs = (Map)initMessage.getField(NodeMessage.FIELD_NODE_ATTRS);
    }

    public synchronized void send(NodeMessage responseMessage) throws AppException
    {
        if (getState()!=NodeState.Ready) {
            throw new AppException(ServiceErrorConstants.ERR_NODE_STATE_NOT_READY, "Session "+getConsistentId()+"/"+getId()+" is not ready");
        }
        try{
            wsSession.sendMessage(new TextMessage(responseMessage.toString()));
            lastSentTime = System.currentTimeMillis();
            totalMessagesSent++;
        }catch(Throwable e) {
            close();
            throw new AppException(e, ServiceErrorConstants.ERR_NODE_SEND, "Session "+getConsistentId()+"/"+getId()+" send message failed");
        }
    }

    protected void onMessage(String payload) {
        lastRecvTime = System.currentTimeMillis();
        totalMessagesRecv++;
        nodeService.onSessionMessage(this, payload);
    }

    public void close() {
        if ( getState()==NodeState.Closed ) {
            return;
        }
        changeState(NodeState.Closing);
        wsSession.getAttributes().remove(ATTR_SESSION);
        //TPDP close Jetty webssocket session
        try {
            wsSession.close();
        }catch(Throwable t) {}
        if ( wsSession instanceof NativeWebSocketSession) {
            Object nativeSession = ((NativeWebSocketSession)wsSession).getNativeSession();
            if ( nativeSession!=null && nativeSession instanceof org.eclipse.jetty.websocket.api.Session){
                org.eclipse.jetty.websocket.api.Session jettySession = (org.eclipse.jetty.websocket.api.Session)nativeSession;
                try {
                    jettySession.disconnect();
                } catch (Throwable e) {}
            }
        }
        wsSession = null;
        changeState(NodeState.Closed);
        nodeService.onSessionClosed(this);
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", getId());
        if ( !StringUtil.isEmpty(consistentId)) {
            json.addProperty("consistentId", getConsistentId());
        }
        json.addProperty("type", getType().name());
        json.addProperty("state", getState().name());
        json.addProperty("stateTime", stateTime);
        if ( attrs!=null ) {
            json.add("attrs", JsonUtil.object2json(attrs));
        }
        if ( topics!=null ) {
            json.add("topics", JsonUtil.object2json(topics));
        }
        json.addProperty("remotedAddr", getRemoteAddress().toString());
        json.addProperty("creationTime", creationTime);
        json.addProperty("lastRecvTime", lastRecvTime);
        json.addProperty("lastSentTime", lastSentTime);
        json.addProperty("totalMessagesSent", totalMessagesSent);
        json.addProperty("totalMessagesRecv", totalMessagesRecv);
        return json;
    }

}
