package trader.service.tradlet;

import java.util.Map;
import java.util.Properties;

import trader.common.beans.BeansContainer;
import trader.common.exchangeable.Exchangeable;
import trader.service.trade.AccountView;

public class TradletContextImpl implements TradletContext, BeansContainer {

    private TradletGroupTemplate template;
    private Properties config;
    private TradletGroupImpl group;

    TradletContextImpl(TradletGroupImpl group, TradletGroupTemplate template, Properties config)
    {
        this.group = group;
        this.template = template;
        this.config = config;
    }

    @Override
    public BeansContainer getBeansContainer() {
        return this;
    }

    @Override
    public Exchangeable getExchangeable() {
        return template.exchangeable;
    }

    @Override
    public AccountView getAccountView() {
        return template.accountView;
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
