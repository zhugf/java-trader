package trader.service.tradlet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import trader.common.beans.BeansContainer;
import trader.common.exception.AppException;
import trader.common.exchangeable.Exchangeable;
import trader.common.tick.PriceLevel;
import trader.common.util.JsonUtil;
import trader.service.ServiceErrorCodes;
import trader.service.data.KVStore;
import trader.service.trade.Account;
import trader.service.trade.Order;
import trader.service.trade.Transaction;

/**
 * 策略组的实现类.
 * <p>
 * 初始化过程:
 * 1 Group init, 加载 account/instruments/price levels
 * 2 GroupEngine init, register listeners
 * 3 Tradlet init
 */
public class TradletGroupImpl implements TradletGroup, ServiceErrorCodes {
    private static final Logger logger = LoggerFactory.getLogger(TradletGroupImpl.class);

    private String id;
    private TradletService tradletService;
    private BeansContainer beansContainer;
    private String config;
    private TradletGroupState engineState = TradletGroupState.Suspended;
    private TradletGroupState configState = TradletGroupState.Enabled;
    private TradletGroupState state = TradletGroupState.Suspended;
    private List<Exchangeable> instruments;
    private List<PriceLevel> priceLevels;
    private Account account;
    private KVStore kvStore;
    private List<TradletHolder> tradletHolders = new ArrayList<>();
    private List<TradletHolder> enabledTradletHolders = new ArrayList<>();
    private PlaybookKeeperImpl playbookKeeper;
    private long createTime;
    private long updateTime;

    public TradletGroupImpl(TradletService tradletService, BeansContainer beansContainer, String id)
    {
        this.id = id;
        this.tradletService = tradletService;
        this.beansContainer = beansContainer;
        createTime = System.currentTimeMillis();
        playbookKeeper = new PlaybookKeeperImpl(this);
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
    public List<Exchangeable> getInstruments() {
        return instruments;
    }

    @Override
    public List<PriceLevel> getPriceLevels(){
        return priceLevels;
    }

    @Override
    public KVStore getKVStore() {
        return kvStore;
    }

    @Override
    public PlaybookKeeper getPlaybookKeeper() {
        return playbookKeeper;
    }

    public List<TradletHolder> getTradletHolders() {
        return enabledTradletHolders;
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
    public TradletGroupState getConfigState() {
        return configState;
    }

    @Override
    public TradletGroupState getState() {
        return state;
    }

    @Override
    public void setState(TradletGroupState newState) {
        this.engineState = newState;
        changeState();
    }

    /**
     * 某品种的数据是否被关注
     */
    public boolean interestOn(Exchangeable e, PriceLevel level) {
        boolean result = instruments.contains(e);
        if ( result && level!=null ) {
            result = priceLevels.contains(level);
        }

        return result;
    }

    public String getConfig() {
        return config;
    }

    public BeansContainer getBeansContainer() {
        return beansContainer;
    }

    public TradletService getTradletService() {
        return tradletService;
    }

    /**
     * Group第一次初始化
     */
    public void init(TradletGroupTemplate template) throws AppException
    {
        this.config = template.config;
        this.configState = template.state;
        this.instruments = template.instruments;
        this.priceLevels = template.priceLevels;
        this.account = template.account;
        this.tradletHolders = template.tradletHolders;
        this.enabledTradletHolders = new ArrayList<>();
        updateTime = System.currentTimeMillis();
        changeState();
    }

    public void initTradlets()
    {
        for(TradletHolder tradletHolder: tradletHolders) {
            try {
                tradletHolder.init();
                this.enabledTradletHolders.add(tradletHolder);
            }catch(Throwable t) {
                logger.error("Tradlet group "+id+" init failed: "+t, t);
            }
        }
    }

    /**
     * 当配置有变化时, 实现动态更新
     */
    public void reload(TradletGroupTemplate template) throws AppException
    {
        this.config = template.config;
        this.configState = template.state;
        this.instruments = template.instruments;
        this.priceLevels = template.priceLevels;
        this.account = template.account;
        updateTime = System.currentTimeMillis();
        Map<String, TradletHolder> reloadHolders = new HashMap<>();
        for(TradletHolder holder:template.tradletHolders) {
            reloadHolders.put(holder.getId(), holder);
        }
        for(TradletHolder tradletHolder: this.tradletHolders) {
            TradletHolder updateHolder = reloadHolders.remove(tradletHolder.getId());
            try {
                tradletHolder.reload(updateHolder.getContext());
            }catch(Throwable t) {
                throw new AppException(t, ERR_TRADLET_TRADLETGROUP_UPDATE_FAILED, "Tradlet group "+id+" reload tradlet "+tradletHolder.getId()+"+ failed: "+t.toString());
            }
        }
        for(TradletHolder tradletHolder:reloadHolders.values()) {
            try {
                tradletHolder.init();
                tradletHolders.add(tradletHolder);
            }catch(Throwable t) {
                throw new AppException(t, ERR_TRADLET_TRADLETGROUP_UPDATE_FAILED, "Tradlet group "+id+" init tradlet "+tradletHolder.getId()+" failed: "+t.toString());
            }
        }
        List<TradletHolder> enabledTradletHolders = new ArrayList<>();
        for(TradletHolder holder:this.tradletHolders) {
            if ( !holder.isDisabled()) {
                enabledTradletHolders.add(holder);
            }
        }
        this.enabledTradletHolders = enabledTradletHolders;
        changeState();
    }

    /**
     * 找engineTradletGroupState, configTradletGroupState最小值
     */
    private void changeState() {
        TradletGroupState thisState = TradletGroupState.values()[ Math.min(engineState.ordinal(), configState.ordinal()) ];
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
        if ( instruments!=null ) {
            json.add("instruments", JsonUtil.object2json(instruments));
        }
        json.addProperty("account", getAccount().getId());
        json.add("tradlets", JsonUtil.object2json(tradletHolders));
        json.add("playbookKeeper", playbookKeeper.toJson());
        return json;
    }

    /**
     * 更新订单状态
     */
    public void updateOnOrder(Order order) {
        playbookKeeper.updateOnOrder(order);
    }

    public void updateOnTxn(Transaction txn) {
        playbookKeeper.updateOnTxn(txn);
    }

    public void onNoopSecond() {
        playbookKeeper.onNoopSecond();
    }

    public void onPlaybookStateChanged(Playbook playbook, PlaybookStateTuple oldStateTuple) {
        for(int i=0;i<tradletHolders.size();i++) {
            TradletHolder holder = tradletHolders.get(i);
            try{
                holder.getTradlet().onPlaybookStateChanged(playbook, oldStateTuple);
            }catch(Throwable t) {
                if ( holder.setThrowable(t) ) {
                    logger.error("策略组 "+getId()+" 策略 "+holder.getId()+" 更新Playbook "+playbook.getId()+" 状态变化失败: "+t.toString(), t);
                }
            }
        }
    }

}
