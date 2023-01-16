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
    private long timestamp;
    private TradletContext context;
    private Throwable lastThrowable;
    private long lastThrowableTime;

    public TradletHolder(String id, Tradlet tradlet, long timestamp, TradletContext context)
    {
        this.id = id;
        this.tradlet = tradlet;
        this.timestamp = timestamp;
        this.context = context;
    }

    public String getId() {
        return id;
    }

    public Tradlet getTradlet() {
        return tradlet;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public TradletContext getContext() {
        return context;
    }

    public boolean isDisabled() {
        return context==null;
    }

    /**
     * 在TradletGroup线程中独立完成初始化
     */
    public void init() throws Exception
    {
        tradlet.init(context);
    }

    /**
     * 在TradletGroup线程中重新加载配置参数
     */
    public void reload(TradletContext context) throws Exception
    {
        this.context = context;
        tradlet.reload(context);
    }

    public void destroy() throws Exception
    {
        tradlet.destroy();
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("disabled", isDisabled());
        if( context!=null ) {
            json.addProperty("config", context.getConfigText());
        }
        json.addProperty("lastThrowableTime", lastThrowableTime);
        if ( lastThrowable!=null ) {
            json.addProperty("lastThrowable", StringUtil.throwable2string(lastThrowable));
        }
        if ( tradlet instanceof JsonEnabled ) {
            json.add("concreteProps", ((JsonEnabled)tradlet).toJson());
        }
        return json;
    }

    @Override
    public String toString() {
        return toJson().toString();
    }

    /**
     * @return true 需要记录错误, false: 重复日志不需要记录
     */
    public boolean setThrowable(Throwable t) {
        if ( t==null ) {
            return false;
        }
        Throwable t2= lastThrowable;
        lastThrowable = t;
        lastThrowableTime = System.currentTimeMillis();
        boolean shouldLog = false;
        if ( t2!=null && t.toString().equals(t2.toString()) ) {
            shouldLog = false;
        }else {
            shouldLog = true;
        }
        return shouldLog;
    }

}