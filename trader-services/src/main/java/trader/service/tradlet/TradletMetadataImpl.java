package trader.service.tradlet;

import trader.service.plugin.Plugin;
import trader.service.tradlet.Tradlet;
import trader.service.tradlet.TradletMetadata;

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
}
