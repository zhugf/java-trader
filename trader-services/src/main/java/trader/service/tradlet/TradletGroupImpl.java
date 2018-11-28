package trader.service.tradlet;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import trader.common.beans.BeansContainer;
import trader.common.exchangeable.Exchangeable;
import trader.common.util.ConversionUtil;
import trader.common.util.JsonUtil;
import trader.service.data.KVStore;
import trader.service.trade.AccountView;
import trader.service.trade.TradeService;

/**
 * 策略组的实现类
 */
public class TradletGroupImpl implements TradletGroup {

    private String id;
    private State state;
    private AccountView accountView;
    private KVStore kvStore;
    private Properties properties;
    private Map config;

    public TradletGroupImpl(BeansContainer beansContainer, Map config) {
        id = ConversionUtil.toString(config.get("id"));
        update(beansContainer, config);
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public AccountView getAccountView() {
        return accountView;
    }

    @Override
    public List<Exchangeable> getExchangeables() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Properties getProperties() {
        return properties;
    }

    @Override
    public KVStore getKVStore() {
        return kvStore;
    }

    @Override
    public List<Tradlet> getTradlets() {
        return null;
    }

    @Override
    public State getState() {
        return state;
    }

    @Override
    public void setState(State newState) {
        if ( newState!=state ) {
            //TODO 增加状态改变通知接口
            this.state = newState;
        }
    }

    public Map getConfig() {
        return config;
    }

    /**
     * 当配置有变化时, 实现动态更新
     */
    public void update(BeansContainer beansContainer, Map groupConfig) {
        TradeService tradeService = beansContainer.getBean(TradeService.class);
        String accountViewId = ConversionUtil.toString(config.get("accountView"));
        AccountView accountView = tradeService.getAccountView(accountViewId);

        State newState = ConversionUtil.toEnum(State.class, groupConfig.get("state"));
        if ( accountView==null ) {
            newState = State.Disabled;
        }

        setState(newState);
        this.accountView = accountView;
        this.config = groupConfig;
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", getId());
        json.addProperty("state", getState().name());
        json.addProperty("accountView", getAccountView().getId());
        if ( properties!=null ) {
            json.add("properties", JsonUtil.object2json(properties));
        }
        return json;
    }

}
