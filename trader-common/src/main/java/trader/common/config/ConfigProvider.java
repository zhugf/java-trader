package trader.common.config;

import java.io.IOException;
import java.util.Map;

/**
 * 配置项数据加载接口
 */
public interface ConfigProvider {

    public Object getItem(String configPath);

    /**
     * 配置文件的URL
     */
    public String getURL() throws IOException;

    /**
     * Reload the configuration data
     *
     * @return false on no change, true if changed
     *
     * @throws Exception
     */
    public boolean reload() throws Exception;

    public Map<String, String> getItems();

}
