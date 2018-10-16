package trader.service.plugin;

import java.io.File;
import java.util.List;

import trader.common.beans.Lifecycle;

/**
 * 插件生命周期实现类, 由插件实现。从插件管理服务自动发现并加载.
 * <BR>插件首选继承自PluginAwareAdapter类, 而不是直接实现本接口
 * @see PluginAwareAdapter
 */
public interface PluginAware extends Lifecycle {

    /**
     * 当某个配置文件(非jar/class)改变时, 该方法被回调
     */
    public void onFileUpdated(List<File> files);

}
