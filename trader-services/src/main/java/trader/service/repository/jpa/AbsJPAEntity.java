package trader.service.repository.jpa;

import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.MappedSuperclass;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import trader.common.util.ConversionUtil;
import trader.common.util.JsonEnabled;
import trader.common.util.StringUtil;

/**
 * JPA-Hibernate Entity类实现( accountId/tradingDay/state 需要对应的toJson()函数返回正确的数据 )
 */
@MappedSuperclass
public abstract class AbsJPAEntity implements JsonEnabled {

    @Id
    @Column(name = "id", columnDefinition="VARCHAR(64)", nullable = false)
    protected String id;

    @Column(name = "accountId", columnDefinition="VARCHAR(64)" )
    protected String accountId;

    @Column(name = "tradingDay", columnDefinition="VARCHAR(16)" )
    protected String tradingDay;

    @Column(name = "state", columnDefinition="VARCHAR(32)" )
    protected String state;

    @Column(name = "createTime", columnDefinition="BIGINT" )
    protected long createTime;

    @Column(name = "updateTime", columnDefinition="BIGINT" )
    protected long updateTime;

    @Column(name = "attrs", columnDefinition="JSON" )
    protected String attrs;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTradingDay() {
        return tradingDay;
    }

    public void setTradingDay(String tradingDay) {
        this.tradingDay = tradingDay;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getAttrs() {
        return attrs;
    }

    public void setAttrs(String attrs) {
        this.attrs = attrs;
    }

    public void setAttrs(JsonElement json) {
        this.attrs = json.toString();
        JsonObject json2 = json.getAsJsonObject();
        if ( json2.has("tradingDay")) {
            tradingDay = json2.get("tradingDay").getAsString();
        }
        if ( json2.has("state")) {
            state = json2.get("state").getAsString();
        }
        if ( json2.has("accountId")) {
            accountId = json2.get("accountId").getAsString();
        }
        if ( json2.has("createTime"))
            createTime = json2.get("createTime").getAsLong();
        if ( json2.has("updateTime"))
            updateTime = json2.get("updateTime").getAsLong();
    }

    public void beforeSave() {
    }

    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        try {
            if(StringUtil.isEmpty(getAttrs()))
                json = (JsonObject)JsonParser.parseString(getAttrs());
        }catch(Throwable t) {}
        json.addProperty("id", getId());
        json.addProperty("state", getState());
        json.addProperty("tradingDay", getTradingDay());
        json.addProperty("accountId", accountId);
        json.addProperty("createTime", createTime);
        json.addProperty("updateTime", updateTime);
        return json;
    }

}
