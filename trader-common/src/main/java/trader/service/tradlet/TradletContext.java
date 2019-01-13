package trader.service.tradlet;

import java.util.Properties;

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
     * 策略配置
     */
    public Properties getConfig();

}
