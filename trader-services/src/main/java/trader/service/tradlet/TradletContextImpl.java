package trader.service.tradlet;

import java.util.Properties;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import trader.common.beans.BeansContainer;
import trader.common.exchangeable.Exchangeable;
import trader.common.util.JsonUtil;
import trader.common.util.StringUtil;

public class TradletContextImpl implements TradletContext {

    private String configText;
    private Properties configProps;
    private JsonObject configJson;
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

    public boolean addInstrument(Exchangeable e) {
        return group.addInstrument(e);
    }

    @Override
    public Properties getConfigAsProps() {
        if ( configProps==null ) {
            configProps = StringUtil.text2properties(configText);
        }
        return configProps;
    }

    public JsonElement getConfigAsJson() {
        if ( configJson==null ) {
            try {
                configJson = (JsonObject)(new JsonParser()).parse(configText);
            }catch(Throwable t) {};
            if ( configJson==null ) {
                Properties props = StringUtil.text2properties(configText);
                configJson = (JsonObject)JsonUtil.object2json(props);
            }
        }
        return configJson;
    }

    @Override
    public String getConfigText() {
        return configText;
    }

}
