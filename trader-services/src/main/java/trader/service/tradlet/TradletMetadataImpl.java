package trader.service.tradlet;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import trader.service.plugin.Plugin;

/**
 * 交易策略实现类的元数据
 */
public class TradletMetadataImpl implements TradletMetadata {

    private String id;
    private Class<Tradlet> clazz;
    private Plugin plugin;
    private long timestamp;

    public TradletMetadataImpl(String id, Class<Tradlet> clazz, Plugin plugin, long timestamp) {
        this.id = id;
        this.clazz = clazz;
        this.plugin = plugin;
        this.timestamp = timestamp;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public Plugin getPlugin() {
        return plugin;
    }

    @Override
    public Class getConcreteClass() {
        return clazz;
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", getId());
        json.addProperty("class", clazz.getName());
        if ( getPlugin()!=null ) {
            json.addProperty("pluginId", getPlugin().getId());
        }
        json.addProperty("timestamp", timestamp);
        return json;
    }
}
