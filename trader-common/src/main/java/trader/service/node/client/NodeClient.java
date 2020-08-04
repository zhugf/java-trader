package trader.service.node.client;

import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import trader.common.exchangeable.Exchangeable;
import trader.common.util.ConversionUtil;
import trader.common.util.DateUtil;
import trader.common.util.StringUtil;
import trader.service.node.NodeConstants;
import trader.service.node.NodeMessage;

public class NodeClient implements AutoCloseable, NodeConstants {
    private static final Logger logger = LoggerFactory.getLogger(NodeClient.class);

    private static class ReqItem{
        NodeMessage responseMsg;
    }

    private String consistentId;
    private String id;
    private Executor executor;
    private NodeClientListener listener;
    private WebSocketClient wsClient;
    private Session wsSession;
    private NodeClientEndpoint wsEndpoint;
    private URI wsUri;
    private String user;
    private String credential;
    private volatile NodeState state;
    private Map<Integer, ReqItem> reqItems = new ConcurrentHashMap<>();
    private int syncReqTimeout = 30*1000;

    public NodeClient(String consistentId, NodeClientListener listener) {
        this.listener = listener;
        state = NodeState.NotConfigured;
        wsEndpoint = new NodeClientEndpoint(this);
    }

    public String getConsistentId() {
        return consistentId;
    }

    public String getId() {
        return id;
    }

    public NodeState getState() {
        return state;
    }

    public URI getURI() {
        return wsUri;
    }

    public Executor getExecutor() {
        return executor;
    }

    public void connect(URI uri, String user, String credential) throws Exception
    {
        SslContextFactory sslContextFactory = new SslContextFactory(true);
        wsClient = new WebSocketClient(sslContextFactory);
        wsClient.getPolicy().setIdleTimeout(10*60*1000);
        wsClient.getPolicy().setMaxTextMessageBufferSize(MAX_MESSAGE_SIZE);
        wsClient.getPolicy().setMaxBinaryMessageBufferSize(MAX_MESSAGE_SIZE);
        wsClient.start();
        executor = wsClient.getHttpClient().getExecutor();
        changeState(NodeState.Connecting);
        ClientUpgradeRequest request = new ClientUpgradeRequest();
        //Basic Authentication
        byte[] data = Base64.getEncoder().encode( (user+":"+credential).getBytes(StringUtil.UTF8) );
        request.setHeader("Authorization", "Basic "+new String(data, StringUtil.UTF8) );
        if ( logger.isInfoEnabled() ) {
            logger.info("Connect to trader broker "+uri+" with user "+user);
        }
        this.wsUri = uri;
        this.user = user;
        this.credential = credential;
        wsClient.connect(wsEndpoint, uri, request);
    }

    @Override
    public void close()
    {
        doClose();
    }

    public String syncQryData(Exchangeable exchangeable, String dataInfo, LocalDate tradingDay) throws IOException
    {
        NodeMessage req = new NodeMessage(MsgType.DataQueryReq);
        req.setField(NodeMessage.FIELD_EXCHANGEABLE, exchangeable.toString());
        req.setField(NodeMessage.FIELD_DATA_INFO, dataInfo);
        if ( tradingDay!=null ) {
            req.setField(NodeMessage.FIELD_TRADING_DAY, DateUtil.date2str(tradingDay));
        }
        String result = null;
        NodeMessage response = syncReq(req);
        if ( response!=null ) {
            result = ConversionUtil.toString(response.getField(NodeMessage.FIELD_DATA));
        }
        return result;
    }

    /**
     * 订阅消息主题
     *
     * @param topics
     */
    public void topicSub(List<String> topics) throws IOException
    {
        NodeMessage req = new NodeMessage(MsgType.TopicSubReq);
        req.setField(NodeMessage.FIELD_TOPICS, topics);
        doSend(req);
        //syncReq(req);
    }

    /**
     * 发布消息主题
     * @param topic
     * @param topicData
     */
    public void topicPub(String topic, Map<String,Object> topicData) throws IOException
    {
        NodeMessage req = new NodeMessage(MsgType.TopicPubReq);
        req.setField(NodeMessage.FIELD_TOPIC, topic);
        req.setFields(topicData);
        doSend(req);
        //syncReq(req);
    }

    private NodeMessage syncReq(NodeMessage reqMessage) throws IOException
    {
        ReqItem item = new ReqItem();
        reqItems.put(reqMessage.getId(), item);
        try{
            synchronized(item) {
                doSend(reqMessage);
                try{
                    item.wait(syncReqTimeout);
                }catch(Throwable t) {}
            }
        }finally {
            reqItems.remove(reqMessage.getId());
        }
        NodeMessage result = item.responseMsg;
        return result;
    }

    void onConnect(Session session)
    {
        this.wsSession = session;
        changeState(NodeState.Initializing);
        if ( logger.isInfoEnabled() ) {
            logger.info("Trader broker session is connected: "+session);
        }
        executor.execute(()->{
            doInit();
        });
    }

    void onClose(Session session, int statusCode, String reason)
    {
        if ( logger.isInfoEnabled() ) {
            logger.info("Trader broker session is closed: "+session);
        }
        if ( getState()!=NodeState.Closed ) {
            changeState(NodeState.Closing);
            doClose();
        }
    }

    void onMessage(Session session, String msgText)
    {
        System.out.println("Trader broker "+getURI()+" recv msg: "+msgText);
        if ( logger.isDebugEnabled() ) {
            logger.debug("Trader broker "+getURI()+" recv msg: "+msgText);
        }
        NodeMessage msg = null, respMessage = null;
        try{
            msg = NodeMessage.fromString(msgText);
        }catch(Exception e){
            logger.error("Message parse failed: ", e);
            return;
        }
        switch(msg.getType()) {
        case Ping:
            respMessage = msg.createResponse();
            break;
        case InitResp:
            if ( msg.getErrCode()!=0 ) {
                logger.info("Trader broker "+wsUri+" initialize failed: "+msg.getErrCode()+" "+msg.getErrMsg());
                doClose();
            } else {
                this.id = ConversionUtil.toString(msg.getField(NodeMessage.FIELD_NODE_ID));
                logger.info("Node "+consistentId+"/"+id+" to "+wsUri+" is initialized");
                changeState(NodeState.Ready);
            }
            break;
        case CloseReq:
        case CloseResp:
            doClose();
            break;
        case TopicPush:
            doPush(msg);
            break;
        default:
            if ( msg.getType().isResponse() ) {
                ReqItem item = reqItems.get(msg.getReqId());
                if ( item!=null ) {
                    item.responseMsg = msg;
                    synchronized(item) {
                        item.notify();
                    }
                }
            }
            break;
        }
        if ( respMessage!=null ) {
            try{
                doSend(respMessage);
            }catch(Throwable t) {
                doClose();
            }
        }
    }

    void onError(Session session, Throwable cause)
    {
        if ( logger.isInfoEnabled() ) {
            logger.info("Trader broker session got error: "+session+" cause: "+cause);
        }
        if ( getState()!=NodeState.Closed ) {
            changeState(NodeState.Closing);
            doClose();
        }
    }

    private void doInit() {
        NodeMessage initReq = new NodeMessage(MsgType.InitReq);
        initReq.setField(NodeMessage.FIELD_USER, user);
        initReq.setField(NodeMessage.FIELD_CREDENTIAL, credential);
        initReq.setField(NodeMessage.FIELD_NODE_TYPE, NodeType.GenericClient.name());
        initReq.setField(NodeMessage.FIELD_NODE_CONSISTENT_ID, consistentId);
        initReq.setField(NodeMessage.FIELD_NODE_ID, id);
        try{
            doSend(initReq);
        }catch(Throwable t) {
            doClose();
        }
    }

    private void doClose() {
        if ( NodeState.Closed==state ) {
            return;
        }
        if ( null!=wsClient ) {
            try{
                wsClient.destroy();
            }catch(Throwable t) {}
            wsClient = null;
        }
        changeState(NodeState.Closed);
    }

    private void doSend(NodeMessage msg) throws IOException
    {
        System.out.println("Trader broker "+getURI()+" send message "+msg);
        if ( logger.isDebugEnabled()) {
            logger.debug("Trader broker "+getURI()+" send message "+msg);
        }
        wsSession.getRemote().sendString(msg.toString());
    }

    private void doPush(NodeMessage msg) {
        String topic = ConversionUtil.toString(msg.getField(NodeMessage.FIELD_TOPIC));
        Map<String, Object> topicData = new HashMap<>(msg.getFields());
        topicData.remove(NodeMessage.FIELD_TOPIC);
        if ( listener!=null ) {
            listener.onTopicPub(topic, topicData);
        }
    }

    private void changeState(NodeState state0) {
        if ( state!=state0 ) {
            NodeState oldState = state;
            state = state0;
            if ( listener!=null ) {
                listener.onStateChanged(this, oldState);
            }
        }
    }

}
