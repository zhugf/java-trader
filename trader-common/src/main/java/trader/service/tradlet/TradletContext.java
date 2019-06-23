package trader.service.tradlet;

import java.util.Properties;

import com.google.gson.JsonElement;

import trader.common.beans.BeansContainer;

/**
 * 交易微策略的上下文
 */
public interface TradletContext {

    /**
     * 全局服务获取接口
     */
    public BeansContainer getBeansContainer();

    /**
     * 交易分组
     */
    public TradletGroup getGroup();

    /**
     * 多行文本方式的策略配置文本
     */
    public String getConfigText();

    /**
     * 以Properties对象解析配置文本
     */
    public Properties getConfigAsProps();

    /**
     * 以JSON格式解析配置文本
     */
    public JsonElement getConfigAsJson();
}
