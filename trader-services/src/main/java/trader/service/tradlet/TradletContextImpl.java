package trader.service.tradlet;

import java.util.Properties;

import trader.common.beans.BeansContainer;
import trader.common.util.StringUtil;

public class TradletContextImpl implements TradletContext {

    private String configText;
    private Properties config;
    private TradletGroupImpl group;

    TradletContextImpl(TradletGroupImpl group, String configText)
    {
        this.group = group;
        this.configText = configText;
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
        if ( config==null ) {
            config = StringUtil.text2properties(configText);
        }
        return config;
    }

    @Override
    public String getConfigText() {
        return configText;
    }

}
