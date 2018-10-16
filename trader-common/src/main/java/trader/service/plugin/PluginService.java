package trader.service.plugin;

import java.util.List;

/**
 * 插件发现和管理。
 * <BR>运行时只能修改和增加，不能删除
 */
public interface PluginService {

    /**
     * 返回当前管理的所有插件
     */
    public List<Plugin> getAllPlugins();

    public Plugin getPlugin(String pluginId);

    /**
     * 根据插件属性找到合适的插件.
     * <BR>查询语法: key1=expr1;key2=expr2;key3=expr3
     * <BR>查询结果根据key1,key2,key3排序
     */
    public List<Plugin> search(String queryExpr);

    public void registerListener(PluginListener listener);

    public void deregisterListener(PluginListener listener);
}
