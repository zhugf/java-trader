package trader.service.node;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.PostConstruct;

import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
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
import trader.common.util.ConversionUtil;
import trader.common.util.EncryptionUtil;
import trader.common.util.JsonUtil;
import trader.common.util.StringUtil;
import trader.common.util.SystemUtil;
import trader.common.util.TraderHomeUtil;
import trader.common.util.UUIDUtil;
import trader.service.md.MarketDataService;
import trader.service.plugin.PluginService;
import trader.service.stats.StatsCollector;
import trader.service.stats.StatsItem;
import trader.service.trade.TradeService;
import trader.service.tradlet.TradletService;

//@Component
public class NodeClientChannelImpl implements NodeClientChannel, WebSocketHandler {
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

    private AtomicLong totalMsgSent = new AtomicLong();

    private AtomicLong totalMsgRecv = new AtomicLong();

    @PostConstruct
    public void init() {
        consistentId = SystemUtil.getHostName()+"."+System.getProperty(TraderHomeUtil.PROP_TRADER_CONFIG_NAME);
        statsCollector.registerStatsItem(new StatsItem(NodeClientChannel.class.getSimpleName(), "totalMsgSent"),  (StatsItem itemInfo) -> {
            return totalMsgSent.get();
        });
        statsCollector.registerStatsItem(new StatsItem(NodeClientChannel.class.getSimpleName(), "totalMsgRecv"),  (StatsItem itemInfo) -> {
            return totalMsgRecv.get();
        });
        statsCollector.registerStatsItem(new StatsItem(NodeClientChannel.class.getSimpleName(), "currConnState"),  (StatsItem itemInfo) -> {
            return state.ordinal();
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
        if ( getState() != NodeState.Closed ) {
            try{
                sendMessage(new NodeMessage(MsgType.CloseReq));
            }catch(Throwable t) {}
            closeWsSession(wsSession);
        }
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> wsMessage) throws Exception {
        lastRecvTime = System.currentTimeMillis();
        totalMsgRecv.incrementAndGet();
        if ( logger.isDebugEnabled() ){
            logger.debug("Message: "+wsMessage.getPayload().toString());
        }
        NodeMessage msg = null, respMessage = null;
        try{
            msg = NodeMessage.fromString(wsMessage.getPayload().toString());
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
                logger.info("Trader broker "+wsUrl+" initialize failed: "+msg.getErrCode()+" "+msg.getErrMsg());
                closeWsSession(session);
            } else {
                this.localId = ConversionUtil.toString(msg.getField(NodeMessage.FIELD_NODE_ID));
                changeState(NodeState.Ready);
                logger.info("Node "+consistentId+"/"+localId+" to "+wsUrl+" is initialized");
            }
            break;
        case CloseResp:
            closeWsSession(session);
            break;
        case ControllerInvokeReq:
            respMessage = controllerInvoke(requestMappingHandlerMapping, msg);
            break;
        case NodeInfoReq:
            respMessage = msg.createResponse();
            fillNodeProps(respMessage);
        default:
            break;
        }
        if ( respMessage!=null ) {
            sendMessage(respMessage);
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
        closeWsSession(session);
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
        closeWsSession(session);
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    public boolean sendMessage(NodeMessage message){
        boolean result = false;
        if ( getState()==NodeState.Ready ) {
            try {
                if ( logger.isDebugEnabled() ) {
                    logger.debug("Send message: "+message.toString());
                }
                wsSession.sendMessage(new TextMessage(message.toString()));
                lastSentTime = System.currentTimeMillis();
                totalMsgSent.incrementAndGet();
                result = true;
            } catch (Throwable e) {
                result = false;
                logger.error("Send message failed: ", e);
                closeWsSession(wsSession);
            }
        }
        return result;
    }

    private void sendInitReq() {
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
        sendMessage(initReq);
    }

    private void fillNodeProps(NodeMessage message) {
        message.setField(NodeMessage.FIELD_NODE_TYPE, NodeMessage.NodeType.Trader);
        message.setField(NodeMessage.FIELD_NODE_CONSISTENT_ID, consistentId);
        JsonObject nodeProps = TraderHomeUtil.toJson();
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
        SslContextFactory sslContextFactory = new SslContextFactory(true);
        jettyWsClient = new WebSocketClient(sslContextFactory);
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

    private void clearWsSession() {
        if ( wsConnManager!=null ){
            try {
                wsConnManager.stop();
            }catch(Throwable t) {}
            wsConnManager = null;
        }
        if ( jettyWsClient!=null ) {
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
            this.state = state;
            this.stateTime = System.currentTimeMillis();
            result = true;
        }
        return result;
    }

    /**
     * 根据path找到并发现REST Controller
     */
    public static NodeMessage controllerInvoke(RequestMappingHandlerMapping requestMappingHandlerMapping, NodeMessage reqMessage) {
        String path = ConversionUtil.toString(reqMessage.getField(NodeMessage.FIELD_PATH));
        //匹配合适的
        Object result = null;
        Throwable t = null;
        RequestMappingInfo reqMappingInfo=null;
        HandlerMethod reqHandlerMethod = null;
        Map<RequestMappingInfo, HandlerMethod> map = requestMappingHandlerMapping.getHandlerMethods();
        for(RequestMappingInfo info:map.keySet()) {
            List<String> matches = info.getPatternsCondition().getMatchingPatterns(path);
            if ( matches.isEmpty() ) {
                continue;
            }
            reqMappingInfo = info;
            reqHandlerMethod = map.get(info);
            break;
        }

        if ( reqMappingInfo==null ) {
            t = new Exception("Controller for "+path+" is not found");
            logger.error("Controller for "+path+" is not found");
        } else {
            MethodParameter[] methodParams = reqHandlerMethod.getMethodParameters();
            Object[] params = new Object[methodParams.length];
            try{
                for(int i=0;i<methodParams.length;i++) {
                    MethodParameter param = methodParams[i];
                    String paramName = param.getParameterName();
                    params[i] = ConversionUtil.toType(param.getParameter().getType(), reqMessage.getField(paramName));
                    if ( params[i]==null && !param.isOptional() ) {
                        throw new IllegalArgumentException("Method parameter "+paramName+" is missing");
                    }
                }
                result = reqHandlerMethod.getMethod().invoke(reqHandlerMethod.getBean(), params);
            }catch(Throwable ex ) {
                if ( ex instanceof InvocationTargetException ) {
                    t = ((InvocationTargetException)ex).getTargetException();
                }else {
                    t = ex;
                }
                logger.error("Invoke controller "+path+" with params "+Arrays.asList(params)+" failed: "+t, t);
            }
        }
        NodeMessage respMessage = reqMessage.createResponse();
        if ( t!=null ) {
            respMessage.setErrCode(-1);
            respMessage.setErrMsg(t.toString());
        } else {
            respMessage.setField(NodeMessage.FIELD_RESULT, JsonUtil.object2json(result));
        }
        return respMessage;
    }

}
