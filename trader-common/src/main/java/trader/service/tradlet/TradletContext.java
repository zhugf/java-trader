package trader.service.tradlet;

import java.util.Properties;

import trader.common.beans.BeansContainer;

public interface TradletContext {

    public BeansContainer getBeansContainer();

    public Properties getConfig();

}
