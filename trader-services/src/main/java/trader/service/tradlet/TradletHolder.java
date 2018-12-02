package trader.service.tradlet;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import trader.common.util.JsonEnabled;
import trader.common.util.StringUtil;
import trader.service.ServiceErrorCodes;

/**
 * Tradlet加载信息
 */
class TradletHolder implements JsonEnabled, ServiceErrorCodes {
    private String id;
    private Tradlet tradlet;
    private Throwable lastThrowable;
    private long lastThrowableTime;

    public TradletHolder(String id, Tradlet tradlet)
    {
        this.id = id;
        this.tradlet = tradlet;
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("lastThrowableTime", lastThrowableTime);
        if ( lastThrowable!=null ) {
            json.addProperty("lastThrowable", StringUtil.throwable2string(lastThrowable));
        }
        return json;
    }

    /**
     * @return true 需要记录错误, false: 重复日志不需要记录
     */
    public boolean setThrowable(Throwable t) {
        Throwable t2= lastThrowable;
        lastThrowable = t;
        if ( t==null ) {
            lastThrowableTime = 0;
        }else {
            lastThrowableTime = System.currentTimeMillis();
        }
        boolean shouldLog = false;
        if ( t!=null ) {
            if ( t2!=null && t.toString().equals(t2.toString())) {
                shouldLog = false;
            }else {
                shouldLog = true;
            }
        }
        return shouldLog;
    }

    public String getId() {
        return id;
    }

    public Tradlet getTradlet() {
        return tradlet;
    }

}