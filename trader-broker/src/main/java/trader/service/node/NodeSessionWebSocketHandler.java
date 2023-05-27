package trader.service.node;

import java.nio.charset.Charset;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import trader.common.util.StringUtil;

public class NodeSessionWebSocketHandler implements WebSocketHandler, NodeConstants {
    private final static Logger logger = LoggerFactory.getLogger(NodeSessionWebSocketHandler.class);

    @Autowired
    private NodeServiceImpl nodeMgmtService;

    @PostConstruct
    public void init() {

    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        session.setTextMessageSizeLimit(MAX_MESSAGE_SIZE);
        session.setBinaryMessageSizeLimit(MAX_MESSAGE_SIZE);
        logger.info(session+" WS connection established");
        NodeSessionImpl nodeSession = nodeMgmtService.onSessionConnected(session);
        session.getAttributes().put(NodeSessionImpl.ATTR_SESSION, nodeSession);
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception
    {
        NodeSessionImpl nodeSession = (NodeSessionImpl)session.getAttributes().get(NodeSessionImpl.ATTR_SESSION);
        if ( nodeSession==null ){
            nodeSession = nodeMgmtService.onSessionConnected(session);
        }
        String text = null;
        Object payload = message.getPayload();
        if ( payload instanceof byte[] ){
            text = new String((byte[])payload, 0, message.getPayloadLength(), StringUtil.UTF8);
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
        NodeSessionImpl agentSession = (NodeSessionImpl)session.getAttributes().get(NodeSessionImpl.ATTR_SESSION);
        if ( agentSession!=null ){
            logger.info("Agent "+agentSession.getId()+" is closed from websocket handler");
            agentSession.close();
            agentSession = null;
        }
        session.getAttributes().remove(NodeSessionImpl.ATTR_SESSION);
        try {
            session.close();
        } catch (Throwable e) {}
    }

}
