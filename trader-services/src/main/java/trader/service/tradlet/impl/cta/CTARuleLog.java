package trader.service.tradlet.impl.cta;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jetty.util.StringUtil;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import trader.common.util.ConversionUtil;
import trader.common.util.JsonEnabled;
import trader.common.util.JsonUtil;

/**
 * CTA规则匹配过程记录
 */
public class CTARuleLog implements CTAConstants, JsonEnabled {
    public final String id;
    public CTARuleState state;
    public String pbId;
    public List<String> logs = new ArrayList<>();

    /**
     * 创建一个全新的规则状态记录
     */
    public CTARuleLog(CTARule rule) {
        this.id = rule.id;
        this.state = CTARuleState.ToEnter;
    }

    public CTARuleLog(JsonObject json) {
        this.id = json.get("id").getAsString();
        this.state = ConversionUtil.toEnum(CTARuleState.class, json.get("state").getAsString());
        if ( json.has("pbId")) {
            this.pbId = json.get("pbId").getAsString();
        }
        this.logs = (List)JsonUtil.json2value(json.get("logs"));
        if ( null==this.logs ) {
            this.logs = new ArrayList<>();
        }
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("state", state.name());
        if ( !StringUtil.isEmpty(pbId)) {
            json.addProperty("pbId", pbId);
        }
        json.add("logs", JsonUtil.object2json(logs));
        return json;
    }

    public void changeState(CTARuleState state0, String log) {
        if ( state!=state0 ) {
            state = state0;
            if (!StringUtil.isEmpty(log)) {
                logs.add(log);
            }
        }
    }

}
