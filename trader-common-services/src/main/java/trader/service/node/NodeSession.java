package trader.service.node;

import java.io.IOException;
import java.net.InetSocketAddress;

import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.adapter.NativeWebSocketSession;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import trader.common.util.JsonEnabled;

/**
 * 代表一个活跃的 Node WebSocket 连接
 */
public class NodeSession implements JsonEnabled {
    public static final String ATTR_SESSION = "nodeSession";

    public static enum SessionState{Initializing, Ready, Closing, Closed};

    private NodeMgmtServiceImpl nodeMgmtService;
    private NodeInfo nodeInfo;
    private String wsId;
    private WebSocketSession wsSession;
    private SessionState wsState;
    private long creationTime;
    private volatile long lastRecvTime;
    private volatile long lastSentTime;

    private long totalMessagesSent;
    private long totalMessagesRecv;

    NodeSession(NodeMgmtServiceImpl nodeMgmtService, WebSocketSession wsSession){
        this.nodeMgmtService = nodeMgmtService;
        this.wsSession = wsSession;
        this.wsState = SessionState.Initializing;
        wsId = wsSession.getId();
        creationTime = System.currentTimeMillis();
    }

    public String getId() {
        return wsId;
    }

    public NodeInfo getNodeInfo() {
        return nodeInfo;
    }

    public void setNodeInfo(NodeInfo nodeInfo) {
        this.nodeInfo = nodeInfo;
    }

    public SessionState getState() {
        return wsState;
    }

    void setState(SessionState s) {
        this.wsState = s;
    }

    public InetSocketAddress getRemoteAddress() {
        return wsSession.getRemoteAddress();
    }

    public long getLastRecvTime() {
        return lastRecvTime;
    }

    public void send(NodeMessage responseMessage) throws IOException
    {
        if (wsSession==null) {
            throw new IOException("Node session is closed");
        }

        wsSession.sendMessage(new TextMessage(responseMessage.toString()));
        lastSentTime = System.currentTimeMillis();
        totalMessagesSent++;
    }

    public void onMessage(String payload) {
        lastRecvTime = System.currentTimeMillis();
        totalMessagesRecv++;
        nodeMgmtService.onSessionMessage(this, payload);
    }

    public void close() {
        if ( nodeMgmtService==null ) {
            return;
        }
        NodeMgmtServiceImpl svc0 = nodeMgmtService;
        nodeMgmtService = null;

        svc0.onSessionClosed(this);
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
        setState(SessionState.Closed);
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", wsId);
        json.addProperty("state", getState().name());
        json.addProperty("remotedAddr", getRemoteAddress().toString());
        json.addProperty("creationTime", creationTime);
        json.addProperty("lastRecvTime", lastRecvTime);
        json.addProperty("lastSentTime", lastSentTime);
        json.addProperty("totalMessagesSent", totalMessagesSent);
        json.addProperty("totalMessagesRecv", totalMessagesRecv);
        return json;
    }

}
