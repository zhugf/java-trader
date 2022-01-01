package trader.common.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class WebResponse implements JsonEnabled {
    public final int code;
    public final String msg;
    public final Object data;
    public WebResponse(int code, String msg) {
        this.code = code;
        this.msg = msg;
        this.data = null;
    }

    public WebResponse(Object data) {
        this.data = data;
        this.code = 0;
        this.msg = null;
    }

    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("code", code);
        if (!StringUtil.isEmpty(msg)) {
            json.addProperty("msg", msg);
        }
        if (null!=data)
            json.add("data", JsonUtil.object2json(data));
        return json;
    }

    public String toString() {
        return toJson().toString();
    }

}
