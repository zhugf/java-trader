package trader.service.plugin;

import java.io.File;
import java.io.FileFilter;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import trader.common.beans.BeansContainer;
import trader.common.util.JsonEnabled;

/**
 * 插件的类加载对象
 * <BR>每次插件的jar/class文件更新后, 原有的Plugin对象的ClassLoader会被更新.
 */
public interface Plugin extends BeansContainer, JsonEnabled {

    public static final String FILE_DESCRIPTOR = "plugin.properties";

    /**
     * 唯一ID
     */
    public static final String PROP_ID = "id";

    /**
     * 导出接口, 最终值会从properties文件和class合并生成
     */
    public static final String PROP_EXPOSED_INTERFACES = "exposedInterfaces";
    /**
     * 是否不重新加载, 缺省false
     */
    public static final String PROP_PERMANENT = "permanent";

    /**
     * 唯一ID
     */
    public String getId();

    /**
     * 对外暴露接口
     */
    public Set<String> getExposedInterfaces();

    /**
     * 参数
     */
    public Properties getProperties();

    /**
     * 返回插件目录
     */
    public File getPluginDirectory();

    /**
     * 插件类加载ClassLoader
     */
    public ClassLoader getClassLoader();

    /**
     * 返回插件相关类URL
     */
    public URL[] getClassLoaderURLs();

    /**
     * 根据接口类名找到实现类
     */
    public Map<String, Class> getBeanClasses(String className);

    /**
     * 根据接口类返回找到的实现类
     */
    public<T> Map<String, Class<T>> getBeanClasses(Class<T> intfaceClass);

    /**
     * 扫描文件, 忽略已知的jars/lib目录
     */
    public List<File> scanFiles(FileFilter filter);

    /**
     * 已经关闭
     */
    public boolean isClosed();

    /**
     * 返回Plugin包含文件的最新事件戳
     */
    public long getLastModified();

}
