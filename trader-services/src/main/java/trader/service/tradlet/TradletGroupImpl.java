package trader.service.tradlet;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import trader.common.beans.BeansContainer;
import trader.common.exchangeable.Exchangeable;
import trader.service.ServiceErrorCodes;
import trader.service.data.KVStore;
import trader.service.trade.AccountView;

/**
 * 策略组的实现类.
 * <BR>策略组的配置格式为JSON格式
 */
public class TradletGroupImpl implements TradletGroup, ServiceErrorCodes {
    private static final Logger logger = LoggerFactory.getLogger(TradletGroupImpl.class);

    private String id;
    private BeansContainer beansContainer;
    private String config;
    private State state;
    private Exchangeable exchangeable;
    private AccountView accountView;
    private KVStore kvStore;
    private List<TradletHolder> tradletHolders = new ArrayList<>();

    public TradletGroupImpl(BeansContainer beansContainer, String id) throws Exception
    {
        this.id = id;
        this.beansContainer = beansContainer;
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
    public Exchangeable getExchangeable() {
        return exchangeable;
    }

    @Override
    public KVStore getKVStore() {
        return kvStore;
    }

    public List<TradletHolder> getTradletHolders() {
        return tradletHolders;
    }

    @Override
    public List<Tradlet> getTradlets(){
        List<Tradlet> result = new ArrayList<>(tradletHolders.size());
        for(TradletHolder holder:tradletHolders) {
            result.add(holder.getTradlet());
        }
        return result;
    }

    @Override
    public State getState() {
        return state;
    }

    @Override
    public void setState(State newState) {
        if ( newState!=state ) {
            this.state = newState;
            logger.info("交易策略组 "+getId()+" 状态改变为: "+state);
        }
    }

    public String getConfig() {
        return config;
    }

    public BeansContainer getBeansContainer() {
        return beansContainer;
    }

    /**
     * 当配置有变化时, 实现动态更新
     */
    public void update(TradletGroupTemplate template) throws Exception
    {
        this.config = template.config;
        this.state = template.state;
        this.exchangeable = template.exchangeable;
        this.accountView = template.accountView;
        this.tradletHolders = template.tradletHolders;
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", getId());
        json.addProperty("state", getState().name());
        if ( exchangeable!=null ) {
            json.addProperty("exchangeable", exchangeable.toString());
        }
        if ( accountView!=null ) {
            json.addProperty("accountView", getAccountView().getId());
        }
        JsonArray tradletArray = new JsonArray();
        for(int i=0;i<tradletHolders.size();i++) {
            tradletArray.add(tradletHolders.get(i).toJson());
        }
        json.add("tradlets", tradletArray);
        return json;
    }

}
