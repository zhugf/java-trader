package trader.service.tradlet;

import java.util.List;
import java.util.Properties;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import trader.common.exchangeable.Exchangeable;
import trader.common.util.JsonUtil;
import trader.service.data.KVStore;
import trader.service.trade.AccountView;

/**
 * 策略组的实现类
 */
public class TradletGroupImpl implements TradletGroup {

    private String id;
    private boolean enabled;
    private AccountView accountView;
    private KVStore kvStore;
    private Properties properties;

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
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean value) {
        this.enabled = value;
    }

    /**
     * 当配置有变化时, 实现动态更新
     */
    public void update() {

    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", getId());
        json.addProperty("enabled", isEnabled());
        json.addProperty("accountView", getAccountView().getId());
        if ( properties!=null ) {
            json.add("properties", JsonUtil.object2json(properties));
        }
        return json;
    }

}
