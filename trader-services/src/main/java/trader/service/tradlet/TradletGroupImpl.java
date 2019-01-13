package trader.service.tradlet;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import trader.common.beans.BeansContainer;
import trader.common.exception.AppException;
import trader.common.exchangeable.Exchangeable;
import trader.common.util.JsonUtil;
import trader.service.ServiceErrorCodes;
import trader.service.data.KVStore;
import trader.service.trade.Account;
import trader.service.trade.Order;

/**
 * 策略组的实现类.
 * <BR>策略组的配置格式为JSON格式
 */
public class TradletGroupImpl implements TradletGroup, ServiceErrorCodes {
    private static final Logger logger = LoggerFactory.getLogger(TradletGroupImpl.class);

    private String id;
    private BeansContainer beansContainer;
    private String config;
    private State engineState = State.Suspended;
    private State configState = State.Enabled;
    private State state = State.Suspended;
    private Exchangeable exchangeable;
    private Account account;
    private KVStore kvStore;
    private List<TradletHolder> tradletHolders = new ArrayList<>();
    private List<Order> allOrders = new ArrayList<>();
    private LinkedList<Order> pendingOrders = new LinkedList<>();
    private List<PlaybookImpl> allPlaybooks = new ArrayList<>();
    private PlaybookImpl currPlaybook = null;
    private long createTime;
    private long updateTime;

    public TradletGroupImpl(BeansContainer beansContainer, String id)
    {
        this.id = id;
        this.beansContainer = beansContainer;
        createTime = System.currentTimeMillis();
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public Account getAccount() {
        return account;
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
    public State getConfigState() {
        return configState;
    }

    @Override
    public State getState() {
        return state;
    }

    @Override
    public void setState(State newState) {
        this.engineState = newState;
        changeState();
    }

    @Override
    public List<Order> getAllOrders() {
        return allOrders;
    }

    @Override
    public List<Order> getPendingOrders() {
        return pendingOrders;
    }

    @Override
    public Order getLastOrder() {
        if ( allOrders.isEmpty() ) {
            return null;
        }
        return allOrders.get(allOrders.size()-1);
    }

    @Override
    public Order getLastPendingOrder() {
        if ( pendingOrders.isEmpty() ) {
            return null;
        }
        return pendingOrders.getLast();
    }

    @Override
    public void cancelAllPendingOrders() {
        Account account = getAccount();
        for(Order order:pendingOrders) {
            if ( order.getStateTuple().getState().isCancelable() ) {
                try {
                    account.cancelOrder(order.getRef());
                } catch (AppException e) {
                    logger.error("Tradlet group "+getId()+" cancel order "+order.getRef()+" failed "+e.toString(), e);
                }
            }
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
    public void update(TradletGroupTemplate template) throws AppException
    {
        try {
            for(TradletHolder tradletHolder: template.tradletHolders) {
                tradletHolder.init();
            }
        }catch(Throwable t) {
            throw new AppException(t, ERR_TRADLET_TRADLETGROUP_UPDATE_FAILED, "Tradlet group "+id+" update failed: "+t.toString());
        }
        this.config = template.config;
        this.configState = template.state;
        this.exchangeable = template.exchangeable;
        this.account = template.account;
        this.tradletHolders = template.tradletHolders;
        updateTime = System.currentTimeMillis();
        changeState();
    }

    /**
     * 找engineState, configState最小值
     */
    private void changeState() {
        State thisState = State.values()[ Math.min(engineState.ordinal(), configState.ordinal()) ];
        if ( thisState!=state ) {
            this.state = thisState;
            logger.info("Tradlet group "+getId()+" change state to "+state);
        }
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", getId());
        json.addProperty("configState", getConfigState().name());
        json.addProperty("state", getState().name());
        json.addProperty("createTime", createTime);
        json.addProperty("updateTime", updateTime);
        if ( exchangeable!=null ) {
            json.addProperty("exchangeable", exchangeable.toString());
        }
        json.addProperty("account", getAccount().getId());
        json.add("tradlets", JsonUtil.object2json(tradletHolders));
        json.addProperty("allOrderCount", allOrders.size());
        json.addProperty("pendingOrderCount", pendingOrders.size());
        return json;
    }

    /**
     * 更新订单状态
     */
    public void updateOnOrder(Order order) {
        if ( order.getStateTuple().getState().isDone() ) {
            pendingOrders.remove(order);
        }
    }

    @Override
    public List<Playbook> getAllPlaybooks() {
        return (List)allPlaybooks;
    }

    @Override
    public Playbook getActivePlaybook() {
        return currPlaybook;
    }

    @Override
    public void createPlaybook(PlaybookBuilder builder) throws AppException {

    }

}
