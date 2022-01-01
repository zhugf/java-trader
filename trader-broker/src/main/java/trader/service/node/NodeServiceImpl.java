package trader.service.node;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableData;
import trader.common.util.ConversionUtil;
import trader.common.util.DateUtil;
import trader.common.util.StringUtil;
import trader.common.util.TraderHomeUtil;
import trader.service.stats.StatsCollector;
import trader.service.stats.StatsItem;

/**
 * 节点管理服务, 目前只在Broker中运行
 */
@Service
public class NodeServiceImpl extends AbsNodeEndpoint implements NodeConstants, NodeService {
    private static final Logger logger = LoggerFactory.getLogger(NodeServiceImpl.class);

    @Autowired
    private StatsCollector statsCollector;

    @Autowired
    private ExecutorService executorService;

    @Autowired
    private ScheduledExecutorService scheduledExecutorService;

    /**
     * 初始化过程中的NodeSession 列表
     */
    private Map<String, NodeSessionImpl> sessions = new ConcurrentHashMap<>();

    private List<NodeServiceListener> listeners = new ArrayList<>();

    @PostConstruct
    public void init() {
        statsCollector.registerStatsItem(new StatsItem(NodeClientChannel.class.getSimpleName(), "currSessions"),  (StatsItem itemInfo) -> {
            return sessions.size();
        });
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onAppReady() {
        scheduledExecutorService.scheduleAtFixedRate(()->{
            checkSessionStates();
        }, PING_INTERVAL, PING_INTERVAL, TimeUnit.MILLISECONDS);
    }

    public void addListener(NodeServiceListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public NodeSession getSession(String nodeId) {
        NodeSession result = sessions.get(nodeId);
        if ( null==result ) {
            for(NodeSessionImpl session:sessions.values()) {
                if ( StringUtil.equals(nodeId, session.getConsistentId()) ){
                    result = session;
                    break;
                }
            }
        }
        return result;
    }

    public Collection<NodeSession> getNodeSessions(){
        List<NodeSession> result = new ArrayList<>(sessions.values());
        return result;
    }

    /**
     * 需要发送消息到主题订阅的活动连接
     */
    @Override
    public void topicPub(String topic, Map<String, Object> topicData) {
        NodeMessage pushMessage = new NodeMessage(TYPE_TOPIC_PUSH);
        if ( null!=topicData ) {
            pushMessage.setFields(topicData);
        }
        doTopicPub(null, pushMessage);
    }

    /**
     * TopicSub只需要在Broker本地记录即可
     */
    @Override
    public void topicSub(String[] topics, NodeTopicListener listener){
        registerTopicListeners(topics, listener);
    }

    public void checkSessionStates() {
        long t = System.currentTimeMillis();
        List<NodeSessionImpl> sessions0 = new ArrayList<>(sessions.values());
        for(NodeSessionImpl session:sessions0) {
            boolean needClose = false;
            switch(session.getState()) {
            case Initializing:
            case Closing:
                if ( (t-session.getStateTime())>=3*1000 ) {
                    needClose = true;
                }
                break;
            case Ready:
                if ( (t-session.getLastRecvTime())>=PING_INTERVAL*3 ) {
                    needClose = true;
                }else if ( (t-session.getLastRecvTime())>=PING_INTERVAL) {
                    //ping
                    try {
                        session.send(new NodeMessage(TYPE_PING_REQ));
                    } catch (Throwable e) {
                        needClose = true;
                    }
                }
                break;
            default:
                needClose = true;
                break;
            }
            if ( needClose ) {
                closeSession(session);
            }
        }
    }

    public NodeSessionImpl onSessionConnected(WebSocketSession wsSession) {
        NodeSessionImpl session = new NodeSessionImpl(this, wsSession);
        sessions.put(session.getId(), session);
        if ( logger.isInfoEnabled() ) {
            logger.info("Node sesion "+session.getId()+" from addr "+session.getRemoteAddress()+" is connected");
        }
        return session;
    }

    public void onSessionClosed(NodeSessionImpl session) {
        closeSession(session);
    }

    public void onSessionMessage(NodeSessionImpl session, String text) {
        if ( logger.isDebugEnabled() ) {
            logger.error("On session "+session.getId()+" message: "+text);
        }
        NodeMessage reqMessage = null;
        try{
            reqMessage = NodeMessage.fromString(text);
        }catch(Throwable t) {
            logger.error("Parse session "+session.getId()+" message "+text+" failed: "+t, t);
            return;
        }
        NodeMessage reqMessage0 = reqMessage;
        NodeMessage respMessage = null;
        NodeState newState = null;
        switch(reqMessage.getType()) {
        case TYPE_INIT_REQ: //作为管理节点, 接收初始化消息, 并对应的创建NodeInfo
            respMessage = initSession(session, reqMessage);
            if ( respMessage.getErrCode()==0 ) {
                newState = NodeState.Ready;
            }else {
                newState = NodeState.Closed;
            }
            break;
        case TYPE_PING_REP: //服务端收到Ping消息直接丢弃
            break;
        case TYPE_CLOSE_REQ: //收到Client端的Close请求, 发送响应后关闭WS连接
            session.changeState(NodeState.Closing);
            respMessage = reqMessage.createResponse();
            newState = NodeState.Closed;
            break;
        case TYPE_CLOSE_REP://从Server端主动发起Close请求的回应
            session.changeState(NodeState.Closing);
            newState = NodeState.Closed;
            break;
        case TYPE_TOPICSUB_REQ:
            Collection<String> topics = (Collection)reqMessage.getField(NodeMessage.FIELD_TOPICS);
            session.setTopics(topics);
            respMessage = reqMessage.createResponse();
            break;
        case TYPE_TOPICPUB_REQ:
            respMessage = reqMessage.createResponse();
            executorService.execute(()->{
                doTopicPub(session, reqMessage0);
            });
            break;
        case TYPE_NODEINFO_REP: //发送给Client更新NodeInfo数据请求的回应
            session.setAttrs((Map)reqMessage.getField(NodeMessage.FIELD_NODE_ATTRS));
            break;
        case TYPE_DATAQUERY_REQ:
            executorService.execute(()->{
                performDataQuery(session, reqMessage0);
            });
            break;
        default:
            logger.error("Unknown msg: "+reqMessage);
            newState = NodeState.Closed;
        }
        if ( respMessage!=null ) {
            NodeState newState0 = doSessionSend(session, respMessage);
            if ( newState0!=null ) {
                newState = newState0;
            }
        }
        if ( newState!=null ) {
            session.changeState(newState);
            if ( newState==NodeState.Ready) {
                asyncNotifySessionAdded(session);
            }
        }
        if ( newState==NodeState.Closed ) {
            closeSession(session);
        }
    }

    private NodeState doSessionSend(NodeSessionImpl session, NodeMessage msg) {
        NodeState result = null;
        try {
            if ( logger.isDebugEnabled() ) {
                logger.error("Send session "+session.getId()+" message: "+msg);
            }
            session.send(msg);
        }catch(Throwable t) {
            result = NodeState.Closed;
            logger.error("Send session "+session.getConsistentId()+"/"+session.getId()+" addr "+session.getRemoteAddress()+" message "+msg+" failed: "+t, t);
        }
        return result;
    }

    /**
     * 初始化WebSocketSession
     */
    private NodeMessage initSession(NodeSessionImpl session, NodeMessage initMessage) {
        NodeMessage result = null;
        //检查用户名/密码
        session.init(initMessage);
        result = initMessage.createResponse();
        result.setField(NodeMessage.FIELD_NODE_ID, session.getId());
        result.setErrCode(0);
        if ( logger.isInfoEnabled()) {
            logger.info("Session "+session.getConsistentId()+"/"+session.getId()+" addr "+session.getRemoteAddress()+" is ready");
        }
        return result;
    }

    /**
     * 关闭WebSocket Session
     */
    private void closeSession(NodeSessionImpl session) {
        if ( sessions.remove(session.getId())!=null ) {
            if ( logger.isInfoEnabled() ) {
                logger.info("Session "+session.getConsistentId()+"/"+session.getId()+" addr "+session.getRemoteAddress()+" is closed");
            }
            asyncNotifySessionClosed(session);
        }
        session.close();
    }

    /**
     * 推送订阅消息到每个节点, 并本地派发消息
     */
    private void doTopicPub(NodeSessionImpl session, NodeMessage msg) {
        String topic = ConversionUtil.toString(msg.getField(NodeMessage.FIELD_TOPIC));
        for(NodeSessionImpl session0:sessions.values()) {
            if ( session0.getState()==NodeState.Ready && session0!=session && session0.isTopicMatch(topic) ) {
                try{
                    session0.send(msg);
                }catch(Throwable t) {
                    closeSession(session0);
                }
            }
        }
        doDispatchTopic(msg);
    }

    /**
     * 执行查询
     */
    private void performDataQuery(NodeSessionImpl session, NodeMessage req) {
        NodeMessage resp = req.createResponse();
        try {
            String exchangeable0 = ConversionUtil.toString(req.getField(NodeMessage.FIELD_EXCHANGEABLE));
            String dataInfo0 = ConversionUtil.toString(req.getField(NodeMessage.FIELD_DATA_INFO));
            LocalDate tradingDay = DateUtil.str2localdate(ConversionUtil.toString(req.getField(NodeMessage.FIELD_TRADING_DAY)));
            ExchangeableData.DataInfo dataInfo = ExchangeableData.DataInfo.parse(dataInfo0);
            Exchangeable exchangeable = Exchangeable.fromString(exchangeable0);
            if ( exchangeable!=null && dataInfo!=null ) {
                ExchangeableData edata = TraderHomeUtil.getExchangeableData();
                String data = edata.load(exchangeable, dataInfo, tradingDay);
                resp.setField(NodeMessage.FIELD_DATA, data);
            }
        } catch(Throwable t) {
            resp.setErrCode(1);
            resp.setErrMsg(t.toString());
        }
        try{
            session.send(resp);
        }catch(Throwable t) {
            closeSession(session);
        }
    }

    protected void asyncNotifySessionAdded(NodeSession session) {
        executorService.execute(()->{
            for(NodeServiceListener listener:listeners) {
                try{
                    listener.onSessionAdded(session);
                }catch(Throwable t) {
                    logger.error("Invoke listener failed", t);
                }
            }
        });
    }

    protected void asyncNotifySessionClosed(NodeSession session) {
        executorService.execute(()->{
            for(NodeServiceListener listener:listeners) {
                try{
                    listener.onSessionClosed(session);
                }catch(Throwable t) {
                    logger.error("Invoke listener failed", t);
                }
            }
        });
    }

}
