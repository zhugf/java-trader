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

public class NodeMessage {
    public static enum MsgType{InitReq, InitResp, Ping, CloseReq, CloseResp};

    public static final String FIELD_TYPE = "type";
    public static final String FIELD_REQID = "reqId";
    public static final String FIELD_CORRID = "corrId";
    public static final String FIELD_ERROR_CODE = "errorCode";
    public static final String FIELD_ERROR_MSG = "errorMsg";

    public static final String FIELD_NODE_ID = "nodeId";
    public static final String FIELD_NODE_PROPS = "nodeProps";

    private MsgType type;
    private int reqId;
    private int corrId;
    private int errCode;
    private String errMsg;

    private Map<String, Object> fields;

    private static AtomicInteger nextReqId = new AtomicInteger();

    public NodeMessage(MsgType type) {
        this(type, nextReqId.incrementAndGet(), null);
    }

    private NodeMessage(MsgType type, int reqId, Map<String,Object> fields) {
        this.type = type;
        this.reqId = reqId;
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
        return new NodeMessage(responseType, getReqId(), null);
    }

    public MsgType getType() {
        return type;
    }

    public int getReqId() {
        return reqId;
    }

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
        json.addProperty(FIELD_REQID, reqId);
        if ( corrId!=0 ) {
            json.addProperty(FIELD_CORRID, corrId);
        }
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
        int reqId = ConversionUtil.toInt(json.remove(FIELD_REQID));
        int corrId = ConversionUtil.toInt(json.remove(FIELD_CORRID));
        int errorCode = ConversionUtil.toInt(json.remove(FIELD_ERROR_CODE));
        String errorMsg = (String)json.remove(FIELD_ERROR_MSG);
        Map<String, Object> fields = json;
        NodeMessage result = new NodeMessage(type, reqId, fields);
        result.setCorrId(corrId);
        result.setErrCode(errorCode);
        result.setErrMsg(errorMsg);
        return result;
    }

}
