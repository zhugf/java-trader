package trader.service.node.client;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketError;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;

import trader.service.node.NodeConstants;

@WebSocket(maxTextMessageSize = NodeConstants.MAX_MESSAGE_SIZE)
public class NodeClientEndpoint {

    private NodeClient nodeClient;

    public NodeClientEndpoint(NodeClient nodeClient) {
        this.nodeClient = nodeClient;
    }

    @OnWebSocketConnect
    public void onConnect(Session session)
    {
        nodeClient.onConnect(session);
    }

    @OnWebSocketClose
    public void onClose(Session session, int statusCode, String reason)
    {
        nodeClient.onClose(session, statusCode, reason);
    }

    @OnWebSocketMessage
    public void onMessage(Session session, String msgText)
    {
        nodeClient.onMessage(session, msgText);
    }

    @OnWebSocketError
    public void onError(Session session, Throwable cause)
    {
        nodeClient.onError(session, cause);
    }
}
