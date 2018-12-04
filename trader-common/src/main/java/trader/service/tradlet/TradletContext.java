package trader.service.tradlet;

import java.util.Properties;

import trader.common.beans.BeansContainer;
import trader.common.exchangeable.Exchangeable;
import trader.service.trade.AccountView;

/**
 * 交易微策略的上下文
 */
public interface TradletContext {
    /**
     * 全局服务获取接口
     */
    public BeansContainer getBeansContainer();

    /**
     * 交易品种
     */
    public Exchangeable getExchangeable();

    public AccountView getAccountView();

    /**
     * 策略配置
     */
    public Properties getConfig();

}
