package trader.service.tradlet;

import java.util.Map;
import java.util.Properties;

import trader.common.beans.BeansContainer;

public class TradletContextImpl implements TradletContext, BeansContainer {

    private TradletGroupImpl group;
    private Properties config;

    TradletContextImpl(TradletGroupImpl group,Properties config)
    {
        this.group = group;
    }

    @Override
    public BeansContainer getBeansContainer() {
        return this;
    }

    @Override
    public Properties getConfig() {
        return config;
    }

    @Override
    public <T> T getBean(Class<T> clazz) {
        if ( clazz==TradletGroup.class ) {
            return (T)group;
        }
        return group.getBeansContainer().getBean(clazz);
    }

    @Override
    public <T> T getBean(Class<T> clazz, String purposeOrId) {
        if ( clazz==TradletGroup.class ) {
            return (T)group;
        }
        return group.getBeansContainer().getBean(clazz, purposeOrId);
    }

    @Override
    public <T> Map<String, T> getBeansOfType(Class<T> clazz) {
        return group.getBeansContainer().getBeansOfType(clazz);
    }

}
