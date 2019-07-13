package trader.service.node;

import java.nio.charset.Charset;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

//@Component
public class NodeMgmtWebSocketHandler implements WebSocketHandler {
    private final static Logger logger = LoggerFactory.getLogger(NodeMgmtWebSocketHandler.class);
    private final static Charset utf8 = Charset.forName("UTF-8");

    @Autowired
    private NodeMgmtServiceImpl nodeMgmtService;

    @PostConstruct
    public void init() {

    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        logger.info(session+" WS connection established");
        NodeSession nodeSession = nodeMgmtService.onSessionConnected(session);
        session.getAttributes().put(NodeSession.ATTR_SESSION, nodeSession);
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception
    {
        NodeSession nodeSession = (NodeSession)session.getAttributes().get(NodeSession.ATTR_SESSION);
        if ( nodeSession==null ){
            nodeSession = nodeMgmtService.onSessionConnected(session);
        }
        String text = null;
        Object payload = message.getPayload();
        if ( payload instanceof byte[] ){
            text = new String((byte[])payload, 0, message.getPayloadLength(), utf8);
        }else{
            text = payload.toString();
        }
        nodeSession.onMessage(text);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        logger.error(session+" got transport error: ", exception);
        closeSession(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        if ( logger.isInfoEnabled() ){
            logger.info(session+" closed "+closeStatus);
        }
        closeSession(session);
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    private void closeSession(WebSocketSession session)
    {
        NodeSession agentSession = (NodeSession)session.getAttributes().get(NodeSession.ATTR_SESSION);
        if ( agentSession!=null ){
            logger.info("Agent "+agentSession.getId()+" is closed from websocket handler");
            agentSession.close();
            agentSession = null;
        }
        session.getAttributes().remove(NodeSession.ATTR_SESSION);
        try {
            session.close();
        } catch (Throwable e) {}
    }

}
