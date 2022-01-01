package trader.service.node;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import trader.common.util.ConversionUtil;
import trader.common.util.JsonEnabled;
import trader.common.util.JsonUtil;

/**
 * 节点之间传递消息
 */
public class NodeMessage implements NodeConstants, JsonEnabled {

    public static final String FIELD_TYPE = "type";
    public static final String FIELD_ID = "id";
    /**
     * 一次req-resp不变
     */
    public static final String FIELD_REQID = "reqId";
    /**
     * 如果有broker中转消息, 那么corrId在多次req-resp之间保持不变
     */
    public static final String FIELD_CORRID = "corrId";
    /**
     * 对于返回消息: errorCode不为0表示出错
     */
    public static final String FIELD_ERROR_CODE = "errorCode";
    /**
     * 对于返回消息: errorMsg表示详细错误消息
     */
    public static final String FIELD_ERROR_MSG = "errorMsg";
    /**
     * 返回消息: 是否还有后续消息
     */
    public static final String FIELD_MORE_DATA = "moreData";

    /**
     * @see #NodeMessage.NodeType
     */
    public static final String FIELD_NODE_TYPE = "nodeType";
    /**
     * Node持久ID, 多次连接保持不变, 由Client一方生成. InitReq设置
     */
    public static final String FIELD_NODE_CONSISTENT_ID = "nodeConsistentId";
    /**
     * Node单次连接ID, 每次唯一. 由Broker一方生成, 在InitResp中返回
     */
    public static final String FIELD_NODE_ID = "nodeId";
    /**
     * Node属性
     */
    public static final String FIELD_NODE_ATTRS = "nodeAttrs";
    /**
     * Topic名称列表
     */
    public static final String FIELD_TOPICS = "topics";
    /**
     * Topic名称
     */
    public static final String FIELD_TOPIC = "topic";
    /**
     * Topic 发布者
     */
    public static final String FIELD_TOPIC_PUBLISHER = "topicPublisher";
    /**
     * 连接用用户名
     */
    public static final String FIELD_USER = "user";
    /**
     * 连接用密码
     */
    public static final String FIELD_CREDENTIAL = "credential";
    /**
     * 合约
     */
    public static final String FIELD_EXCHANGEABLE = "exchangeable";
    /**
     * 交易日
     */
    public static final String FIELD_TRADING_DAY = "tradingDay";
    /**
     * DataInfo
     */
    public static final String FIELD_DATA_INFO = "dataInfo";
    /**
     * 查询数据
     */
    public static final String FIELD_DATA = "data";
    /**
     * ControllerInvoke的URI路径
     */
    public static final String FIELD_PATH = "path";
    public static final String FIELD_RESULT = "result";

    private String type;
    private int id;
    private int reqId;
    private int corrId;
    private int errCode;
    private String errMsg;

    private Map<String, Object> fields;

    private static AtomicInteger nextId = new AtomicInteger();

    public NodeMessage(String type) {
        this(type, nextId.incrementAndGet(), 0, 0, null);
    }

    private NodeMessage(String type, int id, int reqId, int corrId, Map<String,Object> fields) {
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
        String responseType = null;
        if ( type.endsWith(TYPE_SUFFIX_REQ)) {
            responseType = type.substring(0, type.length()-3)+TYPE_SUFFIX_REP;
        }else {
            return null;
        }
        return new NodeMessage(responseType, nextId.incrementAndGet(), getId(), getCorrId(), null);
    }

    public String getType() {
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

    public void setFields(Map<String, Object> values) {
        fields.putAll(values);
    }

    public String toString() {
        return toJson().toString();
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty(FIELD_TYPE, type);
        json.addProperty(FIELD_ID, id);
        json.addProperty(FIELD_CORRID, corrId);
        json.addProperty(FIELD_ERROR_CODE, errCode);
        if ( errCode!=0 ) {
            json.addProperty(FIELD_ERROR_MSG, errMsg);
        }
        for(String key:fields.keySet()) {
            json.add(key, JsonUtil.object2json(fields.get(key)));
        }
        return json;
    }

    public static NodeMessage fromString(String jsonStr) throws Exception
    {
        JsonObject json = JsonParser.parseString(jsonStr).getAsJsonObject();
        String type = json.remove(FIELD_TYPE).getAsString();

        int id = 0, reqId = 0, corrId=0, errorCode=0;
        String errorMsg = null;
        if ( json.has(FIELD_ID)) {
            id = ConversionUtil.toInt(json.remove(FIELD_ID).getAsString());
        }
        if ( json.has(FIELD_REQID) ) {
            reqId = ConversionUtil.toInt(json.remove(FIELD_REQID).getAsString());
        }
        if ( json.has(FIELD_CORRID)) {
            corrId = ConversionUtil.toInt(json.remove(FIELD_CORRID).getAsString());
        }
        if ( json.has(FIELD_ERROR_CODE) ) {
            errorCode = ConversionUtil.toInt(json.remove(FIELD_ERROR_CODE).getAsString());
        }
        if ( json.has(FIELD_ERROR_MSG)) {
            errorMsg = json.remove(FIELD_ERROR_MSG).getAsString();
        }
        Map<String, Object> fields = (Map)JsonUtil.json2value(json);
        NodeMessage result = new NodeMessage(type, id, reqId, corrId, fields);
        result.setErrCode(errorCode);
        result.setErrMsg(errorMsg);
        return result;
    }

}
