package trader.service.tradlet;

import java.util.Properties;

import trader.common.beans.BeansContainer;

public class TradletContextImpl implements TradletContext {

    private Properties config;
    private TradletGroupImpl group;

    TradletContextImpl(TradletGroupImpl group, Properties config)
    {
        this.group = group;
        this.config = config;
    }

    @Override
    public BeansContainer getBeansContainer() {
        return group.getBeansContainer();
    }

    @Override
    public TradletGroup getGroup() {
        return group;
    }

    @Override
    public Properties getConfig() {
        return config;
    }

}
