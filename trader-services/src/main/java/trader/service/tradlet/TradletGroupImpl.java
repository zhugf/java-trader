package trader.service.tradlet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import trader.common.beans.BeansContainer;
import trader.common.exception.AppException;
import trader.common.exception.AppThrowable;
import trader.common.exchangeable.Exchangeable;
import trader.common.util.JsonUtil;
import trader.common.util.StringUtil;
import trader.service.ServiceErrorCodes;
import trader.service.md.MarketData;
import trader.service.md.MarketDataService;
import trader.service.repository.BORepository;
import trader.service.trade.Account;
import trader.service.trade.MarketTimeService;
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
    private BORepository repository;
    private MarketDataService mdService;
    private MarketTimeService mtService;
    private String config;
    private TradletGroupState engineState = TradletGroupState.Suspended;
    private TradletGroupState configState = TradletGroupState.Enabled;
    private TradletGroupState state = TradletGroupState.Suspended;
    private List<Exchangeable> instruments = new ArrayList<>();
    private List<Exchangeable> instruments2 = new ArrayList<>();
    private Account account;
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
        this.repository = beansContainer.getBean(BORepository.class);
        this.mdService = beansContainer.getBean(MarketDataService.class);
        this.mtService = beansContainer.getBean(MarketTimeService.class);
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

    public boolean addInstrument(Exchangeable e) {
        boolean result = false;
        if ( !instruments.contains(e)) {
            instruments.add(e);
            instruments2.add(e);
            result = true;
        }
        return result;
    }

    /**
     * 返回添加的合约品种
     */
    public List<Exchangeable> getUpdatedInstruments(){
        List<Exchangeable> result = instruments2;
        instruments2 = new ArrayList<>();
        return result;
    }

    @Override
    public PlaybookKeeper getPlaybookKeeper() {
        return playbookKeeper;
    }

    public List<TradletHolder> getTradletHolders() {
        return Collections.unmodifiableList(enabledTradletHolders);
    }

    public Tradlet getTradlet(String tradletId) {
        for(int i=0;i<=enabledTradletHolders.size();i++) {
            TradletHolder holder = enabledTradletHolders.get(i);
            if ( holder.getId().equals(tradletId)) {
                return holder.getTradlet();
            }
        }
        return null;
    }

    public String getTradletId(Tradlet tradlet) {
        for(int i=0;i<=enabledTradletHolders.size();i++) {
            TradletHolder holder = enabledTradletHolders.get(i);
            if ( holder.getTradlet()==tradlet) {
                return holder.getId();
            }
        }
        return null;
    }

    @Override
    public List<Tradlet> getTradlets(){
        List<Tradlet> result = new ArrayList<>(enabledTradletHolders.size());
        for(int i=0;i<=enabledTradletHolders.size();i++) {
            TradletHolder holder = enabledTradletHolders.get(i);
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

    public Object onRequest(String path, Map<String, String> params, String payload) {
        Object result = null;
        for(int i=0;i<enabledTradletHolders.size();i++) {
            result = enabledTradletHolders.get(i).getTradlet().onRequest(path, params, payload);
            if ( !StringUtil.isEmpty(result)) {
                break;
            }
        }
        return result;
    }

    /**
     * 某品种的数据是否被关注. 这个函数必须返回非常块
     */
    public boolean interestOn(Exchangeable e) {
        boolean result = instruments.contains(e);
        return result;
    }

    public String getConfig() {
        return config;
    }

    public BeansContainer getBeansContainer() {
        return beansContainer;
    }

    public BORepository getRepository() {
        return repository;
    }

    public MarketDataService getMarketDataService() {
        return mdService;
    }

    public MarketTimeService getMarketTimeService() {
        return mtService;
    }

    public TradletService getTradletService() {
        return tradletService;
    }

    /**
     * Group第一次初始化
     */
    public void init(TradletGroupTemplate groupTemplate) throws AppException
    {
        this.config = groupTemplate.config;
        this.configState = groupTemplate.state;
        this.instruments = groupTemplate.instruments;
        this.account = groupTemplate.account;
        this.playbookKeeper.update(groupTemplate.playbookTemplate);
        this.tradletHolders = groupTemplate.tradletHolders;
        this.enabledTradletHolders = new ArrayList<>();
        //TODO playbook keeper load from data store
        updateTime = System.currentTimeMillis();
        changeState();
    }

    /**
     * 保存数据
     */
    public void destroy() {
        //TODO playbook keeper save data to store
        //playbookKeeper.save();
    }

    public void initTradlets()
    {
        for(TradletHolder tradletHolder: tradletHolders) {
            try {
                tradletHolder.init();
                enabledTradletHolders.add(tradletHolder);
            }catch(Throwable t) {
                logger.error("Tradlet group "+id+" init failed: "+t, t);
            }
        }
    }

    /**
     * 当配置有变化时, 动态更新TradletGroup, 并根据需要动态重新更新Tradlet
     */
    public void reload(TradletGroupTemplate template)
    {
        boolean configChanged = StringUtil.equals(this.config, template.config);
        if ( configChanged ) {
            this.config = template.config;
            this.configState = template.state;
            this.instruments = template.instruments;
            this.account = template.account;
            this.playbookKeeper.update(template.playbookTemplate);
        }

        //Tradlet 三情况:
        //Plugin timestamp有变化的init
        //Plugin timestamp无变化, 但是configChanged, 需要reload
        //Plugin timestamp无变化, configChanged==false, 无动作
        Map<String, TradletHolder> templateHoldersByIds = new HashMap<>();
        for(TradletHolder holder:template.tradletHolders) {
            templateHoldersByIds.put(holder.getId(), holder);
        }
        for(int i=0;i<this.tradletHolders.size();i++) {
            TradletHolder tradletHolder = this.tradletHolders.get(i);
            TradletHolder tradletHolder2 = templateHoldersByIds.remove(tradletHolder.getId());
            try {
                if ( tradletHolder.getTradletTimestamp()!=tradletHolder2.getTradletTimestamp() ) {
                    tradletHolder = tradletHolder2;
                    tradletHolder.init();
                    this.tradletHolders.set(i, tradletHolder);
                } else if (configChanged){
                    tradletHolder.reload(tradletHolder2.getContext());
                }
            }catch(Throwable t) {
                String errorMsg = AppThrowable.error2msg(ERR_TRADLET_TRADLETGROUP_UPDATE_FAILED, "Tradlet group "+id+" init/reload tradlet "+tradletHolder.getId()+"+ failed: "+t.toString());
                logger.error(errorMsg, t);
            }
        }
        //新的Tradlet, 一定是init
        for(TradletHolder tradletHolder:templateHoldersByIds.values()) {
            try {
                tradletHolder.init();
                tradletHolders.add(tradletHolder);
            }catch(Throwable t) {
                String errorMsg = AppThrowable.error2msg(ERR_TRADLET_TRADLETGROUP_UPDATE_FAILED, "Tradlet group "+id+" init tradlet "+tradletHolder.getId()+"+ failed: "+t.toString());
                logger.error(errorMsg, t);
            }
        }
        List<TradletHolder> enabledTradletHolders = new ArrayList<>();
        for(TradletHolder holder:this.tradletHolders) {
            if ( !holder.isDisabled()) {
                enabledTradletHolders.add(holder);
            }
        }
        this.enabledTradletHolders = enabledTradletHolders;
        updateTime = System.currentTimeMillis();
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
            JsonArray instrumentsArr = new JsonArray();
            for(Exchangeable i:instruments) {
                instrumentsArr.add(i.toString());
            }
            json.add("instruments", instrumentsArr);
        }
        if ( account!=null ) {
            json.addProperty("account", getAccount().getId());
        }
        json.add("tradlets", JsonUtil.object2json(enabledTradletHolders));
        Set<String> enabledTradletIds = new HashSet<>();
        for(int i=0;i<enabledTradletHolders.size();i++) {
            enabledTradletIds.add( enabledTradletHolders.get(i).getId());
        }
        List<String> disabledTradletIds = new ArrayList<>();
        for(int i=0; i<tradletHolders.size();i++) {
            String id = tradletHolders.get(i).getId();
            if ( !enabledTradletIds.contains(id)) {
                disabledTradletIds.add(id);
            }
        }
        if ( !disabledTradletIds.isEmpty() ) {
            json.add("disabledTradletIds", JsonUtil.object2json(disabledTradletIds));
        }
        json.add("playbookKeeper", playbookKeeper.toJson());
        return json;
    }

    public void updateOnTick(MarketData tick) {
        playbookKeeper.updateOnTick(tick);
    }

    /**
     * 更新订单状态
     */
    public void updateOnOrder(Order order) {
        playbookKeeper.updateOnOrder(order);
    }

    public void updateOnTxn(Order order, Transaction txn) {
        playbookKeeper.updateOnTxn(order, txn);
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
