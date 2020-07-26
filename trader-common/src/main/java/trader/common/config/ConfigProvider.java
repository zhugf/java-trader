package trader.common.config;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * 配置项数据加载接口
 */
public interface ConfigProvider {

	/**
	 * 将配置参数作为ConfigItem对象列表返回
	 */
	public List<ConfigItem> getItems();

	/**
	 * 保存配置参数, 如果不支持保存则抛出异常
	 */
    public void saveItems(Map<String,String> pathValues) throws Exception;

    /**
     * 配置文件所在的URL, 作为Provider的唯一定位标识. 必须返回值
     */
    public URI getURI() throws Exception;

    /**
     * 重新加载配置内容
     *
     * @return false 未修改, true 自从上一次调用已修改
     */
    public boolean reload() throws Exception;
}
