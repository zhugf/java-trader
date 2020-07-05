package trader.service.repository.jpa;

import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.MappedSuperclass;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * JPA-Hibernate Entity类实现( accountId/tradingDay/state 需要对应的toJson()函数返回正确的数据 )
 */
@MappedSuperclass
public abstract class AbsJPAEntity {

    @Id
    @Column(name = "ID", columnDefinition="VARCHAR(64)", nullable = false)
    protected String id;

    @Column(name = "ACCOUNT_ID", columnDefinition="VARCHAR(64)" )
    protected String accountId;

    @Column(name = "TRADING_DAY", columnDefinition="VARCHAR(16)" )
    protected String tradingDay;

    @Column(name = "STATE", columnDefinition="VARCHAR(32)" )
    protected String state;

    @Column(name = "ATTRS", columnDefinition="MEDIUMTEXT" )
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
    }

}
