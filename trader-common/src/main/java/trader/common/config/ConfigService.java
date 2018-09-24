package trader.common.config;

import java.util.List;

public interface ConfigService {

    public void registerProvider(String sourceId, ConfigProvider provider);

    public ConfigProvider getProvider(String sourceId);

    public List<String> getSources();

    /**
     * 注册配置更新通知接口
     * <BR>sourceId如果为null, 会使用configPaths的项目依次匹配已有的ConfigSource
     *
     * @param sourceId 已知的ConfigSource Id, 缺省 null
     */
    public void addListener(String sourceId, String[] paths, ConfigListener listener);

    /**
     * Async notify that a config source is changed
     */
    public void sourceChange(String source);

}
