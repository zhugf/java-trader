package trader.service.tradlet;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import trader.common.util.JsonEnabled;

/**
 * 交易剧本实现类
 */
public class PlaybookImpl implements Playbook, JsonEnabled {
    private String id;

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getTemplate() {
        return null;
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        return json;
    }

}
