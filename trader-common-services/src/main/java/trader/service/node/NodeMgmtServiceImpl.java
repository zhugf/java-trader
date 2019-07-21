package trader.service.node;

import java.io.IOException;
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
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.socket.WebSocketSession;

import trader.common.util.ConversionUtil;
import trader.common.util.StringUtil;
import trader.service.node.NodeMessage.MsgType;
import trader.service.node.NodeMessage.NodeType;
import trader.service.node.NodeSession.SessionState;
import trader.service.stats.StatsCollector;
import trader.service.stats.StatsItem;

//@Service
public class NodeMgmtServiceImpl implements NodeMgmtService {
    private static final Logger logger = LoggerFactory.getLogger(NodeMgmtServiceImpl.class);

    private static final int CHECK_INTERVAL = 20*1000;

    private static final int PING_INTERVAL = 30*1000;

    @Autowired
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    @Autowired
    private StatsCollector statsCollector;

    /**
     * 所有NodeInfo
     */
    private Map<String, NodeInfo> nodesByConsistentId = new ConcurrentHashMap<>();

    /**
     * 已连接的NodeSession
     */
    private Map<String, NodeInfo> nodesById = new ConcurrentHashMap<>();

    /**
     * 初始化过程中的NodeSession 列表
     */
    private Map<String, NodeSession> initSessions = Collections.synchronizedMap(new HashMap<>());

    @PostConstruct
    public void init() {
        statsCollector.registerStatsItem(new StatsItem(NodeService.class.getSimpleName(), "currActiveNodeCount"),  (StatsItem itemInfo) -> {
            return nodesById.size();
        });
    }

    public NodeInfo getNode(String nodeId) {
        NodeInfo result = nodesById.get(nodeId);
        if ( result==null ) {
            result = nodesByConsistentId.get(nodeId);
        }
        return result;
    }

    public Collection<NodeInfo> getNodes(boolean activeOnly){
        List<NodeInfo> result = new ArrayList<>();
        result.addAll(nodesById.values());
        if (!activeOnly) {
            for(NodeInfo consistentInfo:nodesByConsistentId.values()) {
                if ( !consistentInfo.isActive() ) {
                    result.add(consistentInfo);
                }
            }
        }
        return result;
    }

    @Scheduled(fixedDelay=CHECK_INTERVAL)
    public void checkSessionStates() {
        long t = System.currentTimeMillis();
        List<NodeInfo> toCloseNodes = new ArrayList<>();
        List<NodeInfo> toPingNodes = new ArrayList<>();
        for(NodeInfo node:nodesById.values()) {
            NodeSession session = node.getSession();
            if ( session==null ) {
                continue;
            }
            if ( (t-session.getLastRecvTime())>=PING_INTERVAL*3 ) {
                toCloseNodes.add(node);
            }else if ( (t-session.getLastRecvTime())>=PING_INTERVAL) {
                toPingNodes.add(node);
            }
        }
        //关闭超时Node
        for(NodeInfo node:toCloseNodes) {
            NodeSession session = node.getSession();
            if ( session!=null ) {
                closeSession(session);
            }
        }
        for(NodeInfo node:toPingNodes) {
            NodeSession session = node.getSession();
            if ( session!=null ) {
                try {
                    session.send(new NodeMessage(MsgType.Ping));
                } catch (IOException e) {
                    closeSession(session);
                }
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
            NodeInfo nodeInfo = session.getNodeInfo();
            if ( nodeInfo!=null ) {
                logger.debug("Got node "+nodeInfo.getConsistentId()+"/"+nodeInfo.getId()+" addr "+session.getRemoteAddress()+" message:\n"+text);
            } else {
                logger.debug("Got node addr "+session.getRemoteAddress()+" message:\n"+text);
            }
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
        case ControllerInvokeReq:
            respMessage = NodeServiceImpl.controllerInvoke(requestMappingHandlerMapping, reqMessage);
            break;
        default:
            logger.error("Unknown msg: "+reqMessage);
            newState = SessionState.Closed;
        }
        if ( respMessage!=null ) {
            SessionState newState0 = sendMessage(session, respMessage);
            if ( newState0!=null ) {
                newState = newState0;
            }
        }
        if ( newState!=null ) {
            session.setState(newState);
        }
        if ( newState==SessionState.Closed ) {
            closeSession(session);
        }
    }

    private SessionState sendMessage(NodeSession session, NodeMessage msg) {
        SessionState result = null;
        NodeInfo nodeInfo = session.getNodeInfo();
        if ( logger.isDebugEnabled() ) {
            logger.error("Send message to "+nodeInfo.getConsistentId()+"/"+nodeInfo.getId()+" addr "+session.getRemoteAddress()+":\n"+msg.toString());
        }
        try {
            session.send(msg);
        }catch(Throwable t) {
            result = SessionState.Closed;
            logger.error("Send message to "+nodeInfo.getConsistentId()+"/"+nodeInfo.getId()+" addr "+session.getRemoteAddress()+" failed: "+t, t);
        }
        return result;
    }

    /**
     * 初始化WebSocketSession
     */
    private NodeMessage initSession(NodeSession session, NodeMessage initMessage) {
        NodeMessage result = null;

        initSessions.remove(session.getId());

        String nodeConsistentId = (String)initMessage.getField(NodeMessage.FIELD_NODE_CONSISTENT_ID);
        String nodeId = (String)initMessage.getField(NodeMessage.FIELD_NODE_ID);
        NodeType nodeType = ConversionUtil.toEnum(NodeType.class, initMessage.getField(NodeMessage.FIELD_NODE_TYPE));
        Map nodeProps = (Map)initMessage.getField(NodeMessage.FIELD_NODE_PROPS);
        NodeInfo nodeInfo = null;
        if ( !StringUtil.isEmpty(nodeConsistentId)) {
            nodeInfo = nodesByConsistentId.get(nodeConsistentId);
        }
        if ( nodeInfo==null ) {
            nodeInfo = new NodeInfo(nodeConsistentId, nodeId, nodeType);
            nodesById.put(nodeInfo.getId(), nodeInfo);
        }
        if ( !nodesByConsistentId.containsKey(nodeConsistentId)) {
            nodesByConsistentId.put(nodeConsistentId, nodeInfo);
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
            nodesById.remove(nodeId);
        }
        session.setNodeInfo(null);
        session.close();
        if ( logger.isInfoEnabled() ) {
            logger.info("Node "+nodeId+" session "+session.getId()+" addr "+session.getRemoteAddress()+" connection is closed");
        }
    }

}
