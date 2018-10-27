package trader.service.tactic;

import trader.service.plugin.Plugin;

/**
 * 交易策略实现类的元数据
 */
public class TacticMetadataImpl implements TacticMetadata {

    private String id;
    private Class<Tactic> clazz;
    private Plugin plugin;
    private long timestamp;

    public TacticMetadataImpl(String id, Class<Tactic> clazz, Plugin plugin, long timestamp) {
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
