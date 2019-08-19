package trader.service.md.spi;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import trader.common.beans.BeansContainer;
import trader.common.exchangeable.Exchangeable;
import trader.common.util.ConversionUtil;
import trader.common.util.EncryptionUtil;
import trader.common.util.JsonUtil;
import trader.common.util.StringUtil;
import trader.service.ServiceConstants.ConnState;
import trader.service.md.MarketData;
import trader.service.md.MarketDataProducer;

public abstract class AbsMarketDataProducer<T> implements AutoCloseable, MarketDataProducer<T> {
    private final static Logger logger = LoggerFactory.getLogger(AbsMarketDataProducer.class);

    protected BeansContainer beansContainer;
    protected MarketDataProducerListener listener;
    protected String id;
    protected volatile ConnState state;
    protected volatile long stateTime;
    protected Properties connectionProps;
    protected volatile long tickCount;
    protected int connectCount;
    protected List<String> subscriptions = new ArrayList<>();

    public AbsMarketDataProducer(BeansContainer beansContainer, Map configMap) {
        id = "unknown";
        this.beansContainer = beansContainer;
        state = ConnState.Initialized;
        if ( configMap!=null) {
            id = ConversionUtil.toString(configMap.get("id"));
            connectionProps = StringUtil.text2properties((String)configMap.get("text"));
        }
    }

    public void setListener(MarketDataProducerListener listener) {
        this.listener = listener;
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("provider", getProvider());
        json.addProperty("state", state.name());
        json.addProperty("stateTime", stateTime);
        json.addProperty("tickCount", tickCount);
        json.addProperty("connectCount", connectCount);
        JsonArray a = new JsonArray();
        for(String s:subscriptions) {
            a.add(s);
        }
        json.add("connectionProps", JsonUtil.object2json(connectionProps));
        json.add("subscriptions", a);
        return json;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public Properties getConnectionProps() {
        return connectionProps;
    }

    @Override
    public ConnState getState() {
        return state;
    }

    @Override
    public long getStateTime() {
        return stateTime;
    }

    public long getTickCount() {
        return tickCount;
    }

    public long getConnectCount() {
        return connectCount;
    }

    /**
     * 检查配置是否发生变化
     */
    public boolean configEquals(Map map) {
        Properties connectionProps2 = StringUtil.text2properties((String)map.get("text"));
        return connectionProps.equals(connectionProps2);
    }

    @Override
    public void close() {
        subscriptions.clear();
        close0();
    }

    protected abstract void close0();

    /**
     * 异步连接
     */
    public abstract void connect();

    /**
     * 订阅, 需要等到连接上之后才能调用
     */
    public abstract void subscribe(Collection<Exchangeable> instruments);

    protected void changeStatus(ConnState newStatus) {
        if ( state!=newStatus ) {
            ConnState lastStatus = state;
            this.state = newStatus;
            logger.info(getId()+" status changes from "+lastStatus+" to "+state);
            stateTime = System.currentTimeMillis();
            if ( null!=listener ) {
                listener.onStateChanged(this, lastStatus);
            }
        }
    }

    protected void notifyData(MarketData md) {
        tickCount++;
        listener.onMarketData(md);
    }

    protected static String decrypt(String str) {
        String result = str;
        if ( EncryptionUtil.isEncryptedData(str) ) {
            result = new String( EncryptionUtil.symmetricDecrypt(str), StringUtil.UTF8);
        }
        return result;
    }

}
