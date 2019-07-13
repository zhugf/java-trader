package trader.service.node;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.socket.WebSocketSession;

import trader.service.node.NodeSession.SessionState;
import trader.service.stats.StatsCollector;
import trader.service.stats.StatsItem;

//@Service
public class NodeMgmtServiceImpl implements NodeMgmtService {
    private static final Logger logger = LoggerFactory.getLogger(NodeMgmtServiceImpl.class);

    private static final int CHECK_INTERVAL = 30*1000;

    private static final int PING_INTERVAL = 60*1000;

    @Autowired
    private StatsCollector statsCollector;

    /**
     * 已连接的NodeSession
     */
    private Map<String, NodeInfo> nodes = new ConcurrentHashMap<>();

    /**
     * 初始化过程中的NodeSession 列表
     */
    private Map<String, NodeSession> initSessions = Collections.synchronizedMap(new HashMap<>());

    @PostConstruct
    public void init() {
        statsCollector.registerStatsItem(new StatsItem(NodeService.class.getSimpleName(), "currActiveNodeCount"),  (StatsItem itemInfo) -> {
            return nodes.size();
        });
    }

    public NodeInfo getNode(String nodeId) {
        return nodes.get(nodeId);
    }

    public Collection<NodeInfo> getNodes(boolean activeOnly){
        return Collections.unmodifiableCollection(nodes.values());
    }

    @Scheduled(fixedDelay=CHECK_INTERVAL)
    public void checkSessionStates() {
        long t = System.currentTimeMillis();
        List<NodeInfo> toCloseNodes = new ArrayList<>();
        for(NodeInfo node:nodes.values()) {
            NodeSession session = node.getSession();
            if ( (t-session.getLastRecvTime())>=PING_INTERVAL ) {
                toCloseNodes.add(node);
            }
        }
    }

    public NodeSession onSessionConnected(WebSocketSession wsSession) {
        NodeSession session = new NodeSession(this, wsSession);
        initSessions.put(session.getId(), session);
        if ( logger.isInfoEnabled() ) {
            logger.info("Node sesion "+session.getId()+" from addr "+session.getRemoteAddress()+" is connected");
        }
        return session;
    }

    public void onSessionClosed(NodeSession session) {
        closeSession(session);
    }

    public void onSessionMessage(NodeSession session, String text) {
        if ( logger.isDebugEnabled() ) {
            logger.debug("Got node "+session.getId()+"/"+session.getRemoteAddress()+" message: "+text);
        }
        NodeMessage reqMessage = null;
        try{
            reqMessage = NodeMessage.fromString(text);
        }catch(Throwable t) {
            logger.error("Parse message from "+session.getRemoteAddress()+" failed: "+t, t);
            return;
        }
        NodeMessage respMessage = null;
        SessionState newState = null;
        switch(reqMessage.getType()) {
        case InitReq:
            respMessage = initSession(session, reqMessage);
            if ( respMessage.getErrCode()==0 ) {
                newState = SessionState.Ready;
            }else {
                newState = SessionState.Closed;
            }
            break;
        case Ping: //服务端收到Ping消息直接丢弃
            break;
        case CloseReq:
            session.setState(SessionState.Closing);
            respMessage = reqMessage.createResponse();
            newState = SessionState.Closed;
            break;
        default:
            logger.error("Unknown msg: "+reqMessage);
            newState = SessionState.Closed;
        }
        if ( respMessage!=null ) {
            try {
                session.send(respMessage);
            }catch(Throwable t) {
                newState = SessionState.Closed;
                logger.error("Send message to "+session.getRemoteAddress()+" failed: "+t, t);
            }
        }
        if ( newState!=null ) {
            session.setState(newState);
        }
        if ( newState==SessionState.Closed ) {
            closeSession(session);
        }
    }

    /**
     * 初始化WebSocketSession
     */
    private NodeMessage initSession(NodeSession session, NodeMessage initMessage) {
        NodeMessage result = null;

        initSessions.remove(session.getId());

        String nodeId = (String)initMessage.getField("nodeId");
        Map nodeProps = (Map)initMessage.getField("nodeProps");
        NodeInfo nodeInfo = nodes.get(nodeId);
        if ( nodeInfo==null ) {
            nodeInfo = new NodeInfo(nodeId);
            nodes.put(nodeInfo.getId(), nodeInfo);
        }
        nodeInfo.setSession(session);
        nodeInfo.setProps(nodeProps);
        session.setNodeInfo(nodeInfo);
        session.setState(SessionState.Ready);
        result = initMessage.createResponse();
        result.setErrCode(0);
        if ( logger.isInfoEnabled()) {
            logger.info("Node "+nodeInfo.getId()+" session "+session.getId()+" from "+session.getRemoteAddress()+" is ready");
        }
        return result;
    }

    /**
     * 关闭WebSocket Session
     */
    private void closeSession(NodeSession session) {
        String nodeId = "";
        NodeInfo nodeInfo = session.getNodeInfo();
        if ( nodeInfo!=null ) {
            nodeInfo.setSession(null);
            nodeId = nodeInfo.getId();
        }
        session.setNodeInfo(null);
        session.close();
        if ( logger.isInfoEnabled() ) {
            logger.info("Node "+nodeId+" session "+session.getId()+" addr "+session.getRemoteAddress()+" connection is closed");
        }
    }

}
