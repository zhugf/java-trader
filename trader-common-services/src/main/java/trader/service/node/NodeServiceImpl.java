package trader.service.node;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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
import org.springframework.scheduling.annotation.Scheduled;
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
import trader.common.util.JsonUtil;
import trader.common.util.StringUtil;
import trader.common.util.SystemUtil;
import trader.common.util.TraderHomeUtil;
import trader.common.util.UUIDUtil;
import trader.service.md.MarketDataService;
import trader.service.node.NodeMessage.MsgType;
import trader.service.plugin.PluginService;
import trader.service.stats.StatsCollector;
import trader.service.stats.StatsItem;
import trader.service.trade.TradeService;
import trader.service.tradlet.TradletService;

//@Component
public class NodeServiceImpl implements NodeService, WebSocketHandler {
    private static final Logger logger = LoggerFactory.getLogger(NodeServiceImpl.class);

    private static final String ITEM_MGMTURL = "/BasisService/mgmt.url";

    private static final int RECONNECT_INTERVAL = 60*1000;

    @Autowired
    private BeansContainer beansContainer;

    @Autowired
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    @Autowired
    private StatsCollector statsCollector;

    private String consistentId;

    private String localId;

    private String wsUrl;

    private ConnectionState wsConnState = ConnectionState.NotConfigured;

    private WebSocketConnectionManager wsConnManager;

    private WebSocketSession wsSession;

    private volatile long wsRecvTime=0;

    private volatile Throwable wsLastException;

    private int totalConnCount;

    private AtomicLong totalMsgSent = new AtomicLong();

    private AtomicLong totalMsgRecv = new AtomicLong();

    @PostConstruct
    public void init() {
        consistentId = SystemUtil.getHostName()+"."+System.getProperty(TraderHomeUtil.PROP_TRADER_CONFIG_NAME);
        statsCollector.registerStatsItem(new StatsItem(NodeService.class.getSimpleName(), "totalMsgSent"),  (StatsItem itemInfo) -> {
            return totalMsgSent.get();
        });
        statsCollector.registerStatsItem(new StatsItem(NodeService.class.getSimpleName(), "totalMsgRecv"),  (StatsItem itemInfo) -> {
            return totalMsgRecv.get();
        });
        statsCollector.registerStatsItem(new StatsItem(NodeService.class.getSimpleName(), "totalConnCount"),  (StatsItem itemInfo) -> {
            return totalConnCount;
        });
        statsCollector.registerStatsItem(new StatsItem(NodeService.class.getSimpleName(), "currConnState"),  (StatsItem itemInfo) -> {
            return wsConnState.ordinal();
        });
    }

    public String getConsistentId() {
        return consistentId;
    }

    public String getLocalId() {
        return localId;
    }

    public ConnectionState getConnState() {
        return wsConnState;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onAppReady() {
        String mgmtURL = ConfigUtil.getString(ITEM_MGMTURL);
        if ( StringUtil.isEmpty(mgmtURL) ){
            logger.info("Node "+consistentId+" works in standalone mode");
        }else {
            logger.info("Node "+consistentId+" mgmt service: "+mgmtURL);
            wsUrl = mgmtURL+NodeMgmtService.URI_WS_NODEMGMT;
        }
        if ( !StringUtil.isEmpty(wsUrl)) {
            recreateWsConnection(true);
        }
    }

    @EventListener(ContextClosedEvent.class)
    public void onAppClose() {
        if ( wsConnState != ConnectionState.Disconnected ) {
            try{
                sendMessage(new NodeMessage(MsgType.CloseReq));
            }catch(Throwable t) {}
            closeWsSession(wsSession);
        }
    }

    @Scheduled(fixedDelay=RECONNECT_INTERVAL)
    public void checkClientReconnection() {
        if ( StringUtil.isEmpty(wsUrl)) {
            return;
        }
        if ( wsConnState==ConnectionState.Disconnected ) {
            recreateWsConnection(true);
        } else {
            recreateWsConnection(false);
        }
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> wsMessage) throws Exception {
        wsRecvTime = System.currentTimeMillis();
        totalMsgRecv.incrementAndGet();
        if ( logger.isDebugEnabled() ){
            logger.debug("Message: "+wsMessage.getPayload().toString());
        }
        NodeMessage reqMessage = null, respMessage = null;
        try{
            reqMessage = NodeMessage.fromString(wsMessage.getPayload().toString());
        }catch(Exception e){
            logger.error("Message parse failed: ", e);
            return;
        }
        switch(reqMessage.getType()) {
        case Ping:
            respMessage = reqMessage.createResponse();
            break;
        case InitResp:
            if ( reqMessage.getErrCode()!=0 ) {
                logger.info("Node "+consistentId+" to "+wsUrl+" initialize failed: "+reqMessage.getErrCode()+" "+reqMessage.getErrMsg());
                closeWsSession(session);
            }else {
                wsConnState = ConnectionState.Connected;
                logger.info("Node "+consistentId+" to "+wsUrl+" is initialized");
            }
            break;
        case CloseResp:
            closeWsSession(session);
            break;
        case ControllerInvokeReq:
            respMessage = controllerInvoke(requestMappingHandlerMapping, reqMessage);
            break;
        case NodeInfoReq:
            respMessage = reqMessage.createResponse();
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
        boolean canLog=false;
        if (wsLastException ==null || !wsLastException.getMessage().equals(exception.getMessage()) ) {
            canLog=true;
        }
        wsLastException = exception;
        String errMessage = null;

        switch(wsConnState) {
        case Connecting:
            if ( exception instanceof org.eclipse.jetty.websocket.api.UpgradeException ) {
                wsUrl = null;
                wsConnState = ConnectionState.Disconnected;
                errMessage = "Websocket is not supported by "+wsUrl+" : "+exception;
            } else {
                errMessage = "Connect to "+wsUrl+" failed: "+exception;
            }
            if ( canLog ) {
                logger.info(errMessage);
            } else {
                logger.debug(errMessage);
            }
            break;
        default:
            errMessage = "Communicate with "+wsUrl+" got unexpected exception: "+exception;
            if ( canLog ) {
                logger.info(errMessage, exception);
            } else {
                logger.debug(errMessage, exception);
            }
            break;
        }

        closeWsSession(session);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        localId = UUIDUtil.genUUID58();
        logger.info("Node "+consistentId+" to "+wsUrl+" is established");
        this.wsSession = session;
        totalConnCount++;
        wsLastException = null;
        wsConnState = ConnectionState.Initialzing;
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
        if ( wsSession!=null && message!=null ) {
            try {
                if ( logger.isDebugEnabled() ) {
                    logger.debug("Send message: "+message.toString());
                }
                wsSession.sendMessage(new TextMessage(message.toString()));
                wsRecvTime = System.currentTimeMillis();
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
        fillNodeProps(initReq);
        sendMessage(initReq);
    }

    private void fillNodeProps(NodeMessage message) {
        message.setField(NodeMessage.FIELD_NODE_TYPE, NodeMessage.NodeType.Trader);
        message.setField(NodeMessage.FIELD_NODE_CONSISTENT_ID, consistentId);
        message.setField(NodeMessage.FIELD_NODE_ID, localId);
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
        message.setField(NodeMessage.FIELD_NODE_PROPS, nodeProps);
    }

    private void recreateWsConnection(boolean forceRecreate) {
        if ( logger.isDebugEnabled()) {
            logger.debug("recreateWsConnection enter with wsConnState: "+wsConnState);
        }

        switch(wsConnState) {
        case Connected:
            //非强制重连接, 并且活跃时间超出timeout
            if ( !forceRecreate && (System.currentTimeMillis()-wsRecvTime)<3*(60*1000) ) {
                return;
            }
            break;
        case Connecting:
            //正在连接中, 超时退出
            break;
        }

        clearWsSession();
        wsConnManager = createWsConnectionManager(wsUrl);
        try {
            wsRecvTime = System.currentTimeMillis();
            wsConnState = ConnectionState.Connecting;
            if ( wsConnManager!=null ){
                wsConnManager.stop();
                wsConnManager.start();
            }
        }catch(Throwable ise) { //IllegalStateException
            clearWsSession();
            logger.warn("Recreate node connection failed due to: "+ise);
        }
    }

    private WebSocketConnectionManager createWsConnectionManager(String url){
        String wsUrl = url;

        SslContextFactory sslContextFactory = new SslContextFactory(true);
        WebSocketClient jettyWsClient = new WebSocketClient(sslContextFactory); //SystemUtil.asyncExecutor
        jettyWsClient.getPolicy().setIdleTimeout(10*60*1000);
        JettyWebSocketClient wsClient = new JettyWebSocketClient(jettyWsClient);
        return new WebSocketConnectionManager(wsClient, this, wsUrl);
    }

    private void closeWsSession(WebSocketSession session){
        try{
            session.close();
        }catch(Throwable t){}

        if ( wsSession==session ){
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
        wsSession = null;
        wsConnState = ConnectionState.Disconnected;
        wsRecvTime = 0;
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
