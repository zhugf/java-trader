package trader.common.config;

public interface ConfigService {

	/**
	 * 启动时, 从java system properties加载配置文件(或目录)列表, 用,/;表示多个
	 */
	public static final String SYSPROP_CONFIG_FILES = ConfigService.class.getName()+".Files";

	/**
	 * 启动过程注册一个新的配置实现类
	 */
    public void registerProvider(String alias, ConfigProvider provider);

    /**
     * 返回配置路径对应值
     */
    public Object getConfigValue(String path);

    /**
     * 注册配置更新通知接口
     */
    public void addListener(String[] paths, ConfigListener listener);

    /**
     * 删除更新通知接口
     */
    public void removeListener(ConfigListener listener);

    /**
     * 异步通知重新加载配置参数
     */
    public void reload(String providerId);

}
