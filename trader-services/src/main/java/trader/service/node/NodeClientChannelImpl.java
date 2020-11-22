package trader.service.node;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.PostConstruct;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.http.HttpClientTransportOverHTTP;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketConnectionManager;
import org.springframework.web.socket.client.jetty.JettyWebSocketClient;

import com.google.gson.JsonObject;

import trader.common.beans.BeansContainer;
import trader.common.config.ConfigUtil;
import trader.common.exception.AppException;
import trader.common.util.ConversionUtil;
import trader.common.util.EncryptionUtil;
import trader.common.util.JsonUtil;
import trader.common.util.StringUtil;
import trader.common.util.SystemUtil;
import trader.common.util.TraderHomeUtil;
import trader.common.util.UUIDUtil;
import trader.service.ServiceErrorConstants;
import trader.service.md.MarketDataService;
import trader.service.plugin.PluginService;
import trader.service.stats.StatsCollector;
import trader.service.stats.StatsItem;
import trader.service.trade.TradeService;
import trader.service.tradlet.TradletService;

@Service
public class NodeClientChannelImpl extends AbsNodeEndpoint implements NodeClientChannel, WebSocketHandler {
    private static final Logger logger = LoggerFactory.getLogger(NodeClientChannelImpl.class);

    private static final String ITEM_MGMT_URL = "/BasisService/mgmt.url";

    private static final String ITEM_MGMT_USER = "/BasisService/mgmt.user";

    private static final String ITEM_MGMT_CREDENTIAL = "/BasisService/mgmt.credential";

    private static final int RECONNECT_INTERVAL = 60*1000;

    @Autowired
    private BeansContainer beansContainer;

    @Autowired
    private ScheduledExecutorService scheduledExecutorService;

    @Autowired
    private ExecutorService executorService;

    @Autowired
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    @Autowired
    private StatsCollector statsCollector;
    private String consistentId;
    private String localId;
    private String wsUrl;
    private volatile NodeState state = NodeState.NotConfigured;
    private volatile long stateTime = 0;
    private WebSocketConnectionManager wsConnManager;
    private WebSocketClient jettyWsClient;
    private WebSocketSession wsSession;
    private volatile long lastRecvTime=0;
    private volatile long lastSentTime=0;
    private volatile Throwable wsLastException;
    private AtomicLong totalMsgsSent = new AtomicLong();
    private AtomicLong totalMsgsRecv = new AtomicLong();
    private List<NodeClientListener> listeners = new ArrayList<>();



    //------------ Spring Methods ---------------

    @PostConstruct
    public void init() {
        consistentId = SystemUtil.getHostName()+"."+System.getProperty(TraderHomeUtil.PROP_TRADER_CONFIG_NAME);
        statsCollector.registerStatsItem(new StatsItem(NodeClientChannel.class.getSimpleName(), "totalMsgsSent"),  (StatsItem itemInfo) -> {
            return totalMsgsSent.get();
        });
        statsCollector.registerStatsItem(new StatsItem(NodeClientChannel.class.getSimpleName(), "totalMsgsRecv"),  (StatsItem itemInfo) -> {
            return totalMsgsRecv.get();
        });
        statsCollector.registerStatsItem(new StatsItem(NodeClientChannel.class.getSimpleName(), "currConnState"),  (StatsItem itemInfo) -> {
            return state.ordinal();
        });
        statsCollector.registerStatsItem(new StatsItem(NodeClientChannel.class.getSimpleName(), "lastRecvTime"),  (StatsItem itemInfo) -> {
            return lastRecvTime;
        });
        statsCollector.registerStatsItem(new StatsItem(NodeClientChannel.class.getSimpleName(), "lastSentTime"),  (StatsItem itemInfo) -> {
            return lastSentTime;
        });
    }

    public NodeState getState() {
        return state;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onAppReady() {
        scheduledExecutorService.scheduleAtFixedRate(()->{
            if ( isConfigured() ) {
                checkWsConn();
            }
        }, RECONNECT_INTERVAL, RECONNECT_INTERVAL, TimeUnit.MILLISECONDS);
    }

    @EventListener(ContextClosedEvent.class)
    public void onAppClose() {
        if ( getState() != NodeState.Closed && getState()!=NodeState.Closing ) {
            executorService.execute(()->{
                try{
                    doSend(new NodeMessage(MsgType.CloseReq));
                }catch(Throwable t) {}
                closeWsSession(wsSession);
            });
            try{
                Thread.sleep(1000);
            }catch(Throwable t) {}
        }
    }

    //-------------- WebSocketHandler methods -----------------

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> wsMessage) throws Exception {
        lastRecvTime = System.currentTimeMillis();
        totalMsgsRecv.incrementAndGet();
        if ( logger.isDebugEnabled() ){
            logger.debug("Message: "+wsMessage.getPayload().toString());
        }
        NodeMessage req = null, respMessage = null;
        try{
            req = NodeMessage.fromString(wsMessage.getPayload().toString());
        }catch(Exception e){
            logger.error("Message parse failed: ", e);
            return;
        }
        switch(req.getType()) {
        case Ping:
            respMessage = req.createResponse();
            break;
        case InitResp:
            if ( req.getErrCode()!=0 ) {
                logger.info("Trader broker "+wsUrl+" initialize failed: "+req.getErrCode()+" "+req.getErrMsg());
                asyncCloseWsSession(session);
            } else {
                this.localId = ConversionUtil.toString(req.getField(NodeMessage.FIELD_NODE_ID));
                changeState(NodeState.Ready);
                logger.info("Node "+consistentId+"/"+localId+" to "+wsUrl+" is initialized");
            }
            break;
        case CloseResp:
            closeWsSession(session);
            break;
        case ControllerInvokeReq:
            respMessage = NodeHelper.controllerInvoke(requestMappingHandlerMapping, req);
            break;
        case NodeInfoReq:
            respMessage = req.createResponse();
            fillNodeProps(respMessage);
        case TopicPush:
            doDispatchTopic(req);
            break;
        default:
            if ( doResponseNotify(req) ) {
                break;
            }
            //交给Listener处理消息
            for(NodeClientListener listener:this.listeners) {
                respMessage = listener.onMessage(req);
                if ( null!=respMessage ) {
                    break;
                }
            }
            break;
        }
        if ( respMessage!=null ) {
            doSend(respMessage);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        boolean canLog=true;
        if (wsLastException !=null && StringUtil.equals(wsLastException.toString(), exception.toString()) ) {
            canLog=false;
        }
        wsLastException = exception;
        String errMessage = null;

        switch(getState()) {
        case Initializing:
            if ( exception instanceof org.eclipse.jetty.websocket.api.UpgradeException ) {
                errMessage = "Websocket is not supported by "+wsUrl+" : "+exception;
            } else {
                errMessage = "Communicate to "+wsUrl+" failed: "+exception;
            }
            break;
        default:
            errMessage = "Communicate to "+wsUrl+" got unexpected exception: "+exception;
            break;
        }
        if ( canLog ) {
            logger.info(errMessage, exception);
        } else {
            logger.debug(errMessage, exception);
        }
        asyncCloseWsSession(wsSession);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        logger.info("Node "+consistentId+" to "+wsUrl+" is established");
        session.setTextMessageSizeLimit(MAX_MESSAGE_SIZE);
        session.setBinaryMessageSizeLimit(MAX_MESSAGE_SIZE);
        this.wsSession = session;
        wsLastException = null;
        lastSentTime = System.currentTimeMillis();
        lastRecvTime = System.currentTimeMillis();
        changeState(NodeState.Initializing);
        sendInitReq();
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        logger.info("Node "+consistentId+"/"+localId+" to "+wsUrl+" closed");
        asyncCloseWsSession(session);
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    //------------- NodeClientChannel methods ----------------

    /**
     * 发送消息并等待回应
     */
    public NodeMessage syncSend(NodeMessage req, int waitTime, TimeUnit timeUnit) {
        NodeMessage response = null;

        return response;
    }

    public void addListener(NodeClientListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    @Override
    public void topicPub(String topic, Map<String, Object> topicData) throws AppException
    {
        checkState();
        NodeMessage req = new NodeMessage(MsgType.TopicPubReq);
        req.setField(NodeMessage.FIELD_TOPIC, topic);
        req.setFields(topicData);
        doSendAndWait(req, 0);
        //本地派发消息
        doDispatchTopic(req);
    }

    @Override
    public void topicSub(String[] topics, NodeTopicListener listener) throws AppException
    {
        checkState();
        String[] mergedTopics = registerTopicListeners(topics, listener);
        NodeMessage req = new NodeMessage(MsgType.TopicSubReq);
        req.setField(NodeMessage.FIELD_TOPICS, mergedTopics);
        doSendAndWait(req, 0);
    }

    private void checkState() throws AppException
    {
        if ( getState()!=NodeState.Ready) {
            throw new AppException(ServiceErrorConstants.ERR_NODE_STATE_NOT_READY, "Node "+wsUrl+" state is not ready");
        }
    }

    /**
     * 发送消息, 不等待回应
     */
    protected void doSend(NodeMessage message) throws AppException
    {
        try {
            if ( logger.isDebugEnabled() ) {
                logger.debug("Send message: "+message.toString());
            }
            wsSession.sendMessage(new TextMessage(message.toString()));
            lastSentTime = System.currentTimeMillis();
            totalMsgsSent.incrementAndGet();
        } catch (Throwable e) {
            asyncCloseWsSession(wsSession);
            logger.error("Send message failed: ", e);
            throw new AppException(e, ServiceErrorConstants.ERR_NODE_SEND, "Send message to node "+wsUrl+" failed: "+message);
        }
    }

    /**
     * 发送消息并等待回应
     */
    protected NodeMessage doSendAndWait(NodeMessage req, int timeout) throws AppException
    {
        ReqItem reqItem = new ReqItem();
        int reqId = req.getReqId();
        if ( timeout<=0 ) {
            timeout = defaultTimeout;
        }
        pendingReqs.put(reqId, reqItem);
        try {
            doSend(req);
            synchronized(reqItem) {
                try{
                    reqItem.wait(timeout);
                }catch(Throwable t) {}
            }
        }finally {
            pendingReqs.remove(reqId);
        }
        return reqItem.responseMsg;
    }

    private void sendInitReq() throws AppException
    {
        NodeMessage initReq = new NodeMessage(MsgType.InitReq);
        String user = ConfigUtil.getString(ITEM_MGMT_USER);
        String credential = ConfigUtil.getString(ITEM_MGMT_CREDENTIAL);
        if ( EncryptionUtil.isEncryptedData(user) ) {
            user = new String( EncryptionUtil.symmetricDecrypt(user), StringUtil.UTF8);
        }
        if ( EncryptionUtil.isEncryptedData(credential)) {
            credential = new String( EncryptionUtil.symmetricDecrypt(credential), StringUtil.UTF8);
        }
        initReq.setField(NodeMessage.FIELD_USER, user);
        initReq.setField(NodeMessage.FIELD_CREDENTIAL, credential);
        fillNodeProps(initReq);
        doSend(initReq);
    }

    private void fillNodeProps(NodeMessage message) {
        message.setField(NodeMessage.FIELD_NODE_TYPE, NodeMessage.NodeType.Trader);
        message.setField(NodeMessage.FIELD_NODE_CONSISTENT_ID, consistentId);
        JsonObject nodeProps = TraderHomeUtil.toJson();
        {
            com.sun.management.OperatingSystemMXBean bean = (com.sun.management.OperatingSystemMXBean) java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            JsonObject json = new JsonObject();
            json.addProperty("hostName", SystemUtil.getHostName());
            json.add("hostAddrs", JsonUtil.object2json(SystemUtil.getHostIps()) );

            json.addProperty("osArch", bean.getArch());
            json.addProperty("osName", bean.getName());
            json.addProperty("osVersion", bean.getVersion());

            json.addProperty("systemLoadAverage", bean.getSystemLoadAverage());
            json.addProperty("systemCpuLoad", bean.getSystemCpuLoad());
            json.addProperty("totalSwapSpaceSize", bean.getTotalSwapSpaceSize());
            json.addProperty("freeSwapSpaceSize", bean.getFreeSwapSpaceSize());
            json.addProperty("availableProcessors", bean.getAvailableProcessors());
            json.addProperty("freePhysicalMemorySize", bean.getFreePhysicalMemorySize());
            json.addProperty("totalPhysicalMemorySize", bean.getTotalPhysicalMemorySize());

            json.addProperty("processCpuTime", bean.getProcessCpuTime());
            json.addProperty("processCpuLoad", bean.getProcessCpuLoad());
            nodeProps.add("system", json);
        }
        {
            JsonObject json = new JsonObject();
            PluginService pluginService = beansContainer.getBean(PluginService.class);
            json.add("plugins", JsonUtil.object2json(pluginService.getPlugins()));
            nodeProps.add("PluginService", json);
        }
        {
            JsonObject json = new JsonObject();
            MarketDataService mdService = beansContainer.getBean(MarketDataService.class);
            json.add("producers", JsonUtil.object2json(mdService.getProducers()));
            nodeProps.add("MarketDataService", json);
        }
        {
            JsonObject json = new JsonObject();
            TradeService tradeService = beansContainer.getBean(TradeService.class);
            json.add("accounts", JsonUtil.object2json(tradeService.getAccounts()));
            nodeProps.add("TradeService", json);
        }
        {
            JsonObject json = new JsonObject();
            TradletService tradletService = beansContainer.getBean(TradletService.class);
            json.add("tradlets", JsonUtil.object2json(tradletService.getTradletInfos()));
            json.add("groups", JsonUtil.object2json(tradletService.getGroups()));
            nodeProps.add("TradletService", json);
        }
        message.setField(NodeMessage.FIELD_NODE_ATTRS, nodeProps);
    }

    /**
     * 是否已配置服务端地址
     */
    private boolean isConfigured() {
        return !StringUtil.isEmpty(ConfigUtil.getString(ITEM_MGMT_URL));
    }

    /**
     * 定期检查状态, 按需重连
     */
    private void checkWsConn() {
        if ( logger.isDebugEnabled()) {
            logger.debug("recreateWsConnection enter with wsConnState: "+getState());
        }
        boolean needClose = false;
        boolean needConn = false;
        switch(getState()) {
        case Initializing:
        case Ready:
        case Closing:
            //非强制重连接, 并且活跃时间超出 3*PING
            if ( (System.currentTimeMillis()-lastRecvTime)>3*PING_INTERVAL ) {
                needClose = true;
            }
            break;
        case Connecting:
            //正在连接中, 并且时间超出 PING
            if ( (System.currentTimeMillis()-stateTime)>PING_INTERVAL ) {
                needClose = true;
            }
            break;
        case NotConfigured:
        case Closed:
            if ( (System.currentTimeMillis()-stateTime)>RECONNECT_INTERVAL && isConfigured() ) {
                needConn = true;
            }
            break;
        }

        if ( needClose ) {
            clearWsSession();
            return;
        }
        if ( needConn ) {
            try {
                clearWsSession();
                changeState(NodeState.Connecting);
                wsConnManager = createWsConnectionManager();
                lastRecvTime = System.currentTimeMillis();
                localId = UUIDUtil.genUUID58();
                wsConnManager.start();
            }catch(Throwable e) { //IllegalStateException
                clearWsSession();
                logger.warn("Recreate node connection failed due to: "+e);
            }
        }
    }

    private WebSocketConnectionManager createWsConnectionManager(){
        wsUrl = ConfigUtil.getString(ITEM_MGMT_URL);
        HttpClientTransportOverHTTP httpClientTransport = new HttpClientTransportOverHTTP(1);
        SslContextFactory sslContextFactory = new SslContextFactory(true);
        HttpClient httpClient = new HttpClient(httpClientTransport, sslContextFactory);
        httpClientTransport.setHttpClient(httpClient);
        jettyWsClient = new WebSocketClient(httpClient);
        jettyWsClient.getPolicy().setIdleTimeout(10*60*1000);
        JettyWebSocketClient wsClientAdapter = new JettyWebSocketClient(jettyWsClient);
        WebSocketConnectionManager result = new WebSocketConnectionManager(wsClientAdapter, this, wsUrl);

        String user = ConfigUtil.getString(ITEM_MGMT_USER);
        String credential = ConfigUtil.getString(ITEM_MGMT_CREDENTIAL);
        if ( EncryptionUtil.isEncryptedData(user) ) {
            user = new String( EncryptionUtil.symmetricDecrypt(user), StringUtil.UTF8);
        }
        if ( EncryptionUtil.isEncryptedData(credential)) {
            credential = new String( EncryptionUtil.symmetricDecrypt(credential), StringUtil.UTF8);
        }
        if ( !StringUtil.isEmpty(user)) {
            HttpHeaders headers = new HttpHeaders();
            headers.setBasicAuth(user, credential);
            result.setHeaders(headers);
        }
        return result;
    }

    private void closeWsSession(WebSocketSession session){
        if ( null!=session ) {
            try{
                session.close();
            }catch(Throwable t){}
        }
        if ( state!=NodeState.Closed ){
            clearWsSession();
        }
    }

    private void asyncCloseWsSession(WebSocketSession session) {
        executorService.execute(()->{
            closeWsSession(session);
        });
    }

    private void clearWsSession() {
        if ( wsConnManager!=null ){
            try {
                wsConnManager.stop();
            }catch(Throwable t) {}
            wsConnManager = null;
        }
        if ( jettyWsClient!=null ) {
            try{
                jettyWsClient.stop();
            }catch(Throwable t) {}
            try{
                jettyWsClient.destroy();
            }catch(Throwable t) {}
            jettyWsClient = null;
        }
        wsSession = null;
        lastRecvTime = 0;
        lastSentTime = 0;
        changeState(NodeState.Closed);
    }

    private boolean changeState(NodeState state) {
        boolean result = false;
        if ( this.state!=state ) {
            NodeState oldState = this.state;
            this.state = state;
            this.stateTime = System.currentTimeMillis();
            result = true;
            asyncNotifyStateChanged(oldState);
        }
        return result;
    }

    protected void asyncNotifyStateChanged(NodeState oldState) {
        executorService.execute(()->{
            for(NodeClientListener listener:listeners) {
                try{
                    listener.onStateChanged(this, oldState);
                }catch(Throwable t) {
                    logger.error("Invoke listener failed", t);
                }
            }
        });
    }

}
