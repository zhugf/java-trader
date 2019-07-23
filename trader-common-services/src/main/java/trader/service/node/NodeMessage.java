package trader.service.node;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import com.google.gson.JsonObject;

import trader.common.util.ConversionUtil;
import trader.common.util.JsonUtil;

/**
 * 节点之间传递消息
 */
public class NodeMessage {
    /**
     * 消息的分类
     */
    public static enum MsgType{
        /**
         * Client->Admin, 初始化NodeInfo
         */
        InitReq, InitResp,
        /**
         * Admin->Client, 定期PING
         */
        Ping,
        /**
         * Client->Admin, 要求主动关闭
         */
        CloseReq, CloseResp,
        /**
         * Admin->Client, 要求更新NodeProps
         */
        NodeInfoReq, NodeInfoResp,
        /**
         * Client->Admin, 要求调用Controller
         */
        ControllerInvokeReq, ControllerInvokeResp
    };

    /**
     * 节点的分类
     */
    public static enum NodeType{Trader, WebClient, AndroidClient};

    public static final String FIELD_TYPE = "type";
    public static final String FIELD_ID = "id";
    public static final String FIELD_REQID = "reqId";
    public static final String FIELD_CORRID = "corrId";
    public static final String FIELD_ERROR_CODE = "errorCode";
    public static final String FIELD_ERROR_MSG = "errorMsg";

    /**
     * @see #NodeMessage.NodeType
     */
    public static final String FIELD_NODE_TYPE = "nodeType";
    /**
     * Node持久ID, 多次连接保持不变, 由Client一方生成.
     */
    public static final String FIELD_NODE_CONSISTENT_ID = "nodeConsistentId";
    /**
     * Node单次连接ID, 每次唯一. 由Client一方生成
     */
    public static final String FIELD_NODE_ID = "nodeId";
    /**
     * Node属性
     */
    public static final String FIELD_NODE_PROPS = "nodeProps";
    /**
     * ControllerInvoke的URI路径
     */
    public static final String FIELD_PATH = "path";
    public static final String FIELD_RESULT = "result";

    private MsgType type;
    private int id;
    private int reqId;
    private int corrId;
    private int errCode;
    private String errMsg;

    private Map<String, Object> fields;

    private static AtomicInteger nextId = new AtomicInteger();

    public NodeMessage(MsgType type) {
        this(type, nextId.incrementAndGet(), 0, 0, null);
    }

    private NodeMessage(MsgType type, int id, int reqId, int corrId, Map<String,Object> fields) {
        this.type = type;
        this.id = id;
        this.reqId = reqId;
        this.corrId = corrId;
        this.fields = new HashMap<>();
        if( fields!=null ) {
            this.fields.putAll(fields);
        }
    }

    /**
     * 创建返回消息, Message Type 改为返回消息Type, InitReq->InitResp, reqId 保持不变.
     */
    public NodeMessage createResponse() {
        MsgType responseType = null;
        if ( type.name().endsWith("Req")) {
            responseType = MsgType.valueOf(type.name().substring(0, type.name().length()-3)+"Resp");
        } else if ( responseType==MsgType.Ping){
            responseType = type;
        }else {
            throw new RuntimeException("Unsupported msg type "+type);
        }
        return new NodeMessage(responseType, nextId.incrementAndGet(), getId(), getCorrId(), null);
    }

    public MsgType getType() {
        return type;
    }

    /**
     * 每个节点的唯一递增ID
     */
    public int getId() {
        return id;
    }

    /**
     * 对于响应消息, 代表对应的请求消息的ID
     */
    public int getReqId() {
        return reqId;
    }

    /**
     * 代表多条消息之间保持不变的Correlation ID
     */
    public int getCorrId() {
        return corrId;
    }

    public void setCorrId(int id) {
        corrId = id;
    }

    public int getErrCode() {
        return errCode;
    }

    public void setErrCode(int errCode) {
        this.errCode = errCode;
    }

    public String getErrMsg() {
        return errMsg;
    }

    public void setErrMsg(String errMsg) {
        this.errMsg = errMsg;
    }

    public Map<String, Object> getFields() {
        return Collections.unmodifiableMap(fields);
    }

    public Object getField(String field) {
        return fields.get(field);
    }

    public void setField(String field, Object value) {
        if ( value==null ) {
            fields.remove(field);
        }else {
            fields.put(field, value);
        }
    }

    public String toString() {
        JsonObject json = new JsonObject();
        json.addProperty(FIELD_TYPE, type.name());
        json.addProperty(FIELD_ID, id);
        json.addProperty(FIELD_CORRID, corrId);
        json.addProperty(FIELD_ERROR_CODE, errCode);
        if ( errCode!=0 ) {
            json.addProperty(FIELD_ERROR_MSG, errMsg);
        }
        for(String key:fields.keySet()) {
            json.add(key, JsonUtil.object2json(fields.get(key)));
        }
        return json.toString();
    }

    public static NodeMessage fromString(String jsonStr) throws Exception
    {
        JSONObject json = (JSONObject)(new JSONParser()).parse(jsonStr);
        MsgType type = MsgType.valueOf((String) json.remove(FIELD_TYPE));
        int id = ConversionUtil.toInt(json.remove(FIELD_ID));
        int reqId = ConversionUtil.toInt(json.remove(FIELD_REQID));
        int corrId = ConversionUtil.toInt(json.remove(FIELD_CORRID));
        int errorCode = ConversionUtil.toInt(json.remove(FIELD_ERROR_CODE));
        String errorMsg = (String)json.remove(FIELD_ERROR_MSG);
        Map<String, Object> fields = json;
        NodeMessage result = new NodeMessage(type, id, reqId, corrId, fields);
        result.setErrCode(errorCode);
        result.setErrMsg(errorMsg);
        return result;
    }

}
