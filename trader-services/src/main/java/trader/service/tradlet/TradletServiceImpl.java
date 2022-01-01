package trader.service.tradlet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.gson.JsonObject;

import trader.common.beans.BeansContainer;
import trader.common.beans.Discoverable;
import trader.common.beans.ServiceEventHub;
import trader.common.beans.ServiceState;
import trader.common.config.ConfigUtil;
import trader.common.exception.AppException;
import trader.common.util.ConversionUtil;
import trader.common.util.JsonUtil;
import trader.common.util.StringUtil;
import trader.service.ServiceErrorConstants;
import trader.service.beans.DiscoverableRegistry;
import trader.service.md.MarketData;
import trader.service.md.MarketDataService;
import trader.service.plugin.Plugin;
import trader.service.plugin.PluginListener;
import trader.service.plugin.PluginService;
import trader.service.ta.BarService;
import trader.service.trade.TradeService;

/**
 * 交易策略(Tradlet)/策略组(TradletGroup)的管理和事件分发
 */
@Service
public class TradletServiceImpl extends AbsTradletService implements TradletConstants, TradletService, ServiceErrorConstants
{
    private static final Logger logger = LoggerFactory.getLogger(TradletServiceImpl.class);

    @Autowired
    private BeansContainer beansContainer;

    @Autowired
    private MarketDataService mdService;

    @Autowired
    private TradeService tradeService;

    @Autowired
    private BarService taService;

    @Autowired
    private PluginService pluginService;

    @Autowired
    private ExecutorService executorService;

    @Autowired
    private ScheduledExecutorService scheduledExecutorService;

    private Map<String, TradletInfo> tradletInfos = new HashMap<>();

    private ArrayList<TradletGroupEngine> groupEngines = new ArrayList<>();

    private ServiceState state = ServiceState.NotInited;

    public ServiceState getState() {
        return state;
    }

    @PostConstruct
    public void init()
    {
        ServiceEventHub serviceEventHub = beansContainer.getBean(ServiceEventHub.class);
        serviceEventHub.registerServiceInitializer(getClass().getName(), ()->{
            return init0();
        }, pluginService, mdService, tradeService, taService);
    }

    private TradletService init0() {
        state = ServiceState.Starting;
        pluginService.registerListener((List<Plugin> updatedPlugins)->{
            onPluginChanged(updatedPlugins);
        });
        mdService.addListener((MarketData tick)->{
            queueTickEvent(tick);
        });
        Set<String> allTradletIds = new TreeSet<>();
        Set<String> updatedPluginIds = new TreeSet<>();
        tradletInfos = reloadTradletInfos(loadStandardTradlets(), filterTradletPlugins(pluginService.getPlugins()), allTradletIds, null, updatedPluginIds);
        logger.info("Load "+allTradletIds.size()+" tradlets: "+allTradletIds+" from plugins: "+updatedPluginIds);
        reloadGroups();
        scheduledExecutorService.scheduleAtFixedRate(()->{
            queueNoopSecondEvent();
        }, 1000, 1, TimeUnit.SECONDS);
        state = ServiceState.Ready;
        return this;
    }

    @PreDestroy
    public void destroy() {
        //释放tradlet engine的线程
        for(TradletGroupEngine engine:groupEngines) {
            try{
                engine.destroy();
            }catch(Throwable t) {
                logger.error(engine.getGroup().getId()+" release failed: "+t, t);
            }
        }
    }

    @Override
    public Collection<TradletInfo> getTradletInfos() {
        return tradletInfos.values();
    }

    @Override
    public TradletInfo getTradletInfo(String tradletId) {
        TradletInfo result = tradletInfos.get(tradletId);
        if ( result==null ) {
            for(String id0:tradletInfos.keySet()) {
                if ( StringUtil.equalsIgnoreCase(id0, tradletId)) {
                    result = tradletInfos.get(id0);
                    break;
                }
            }
        }
        return result;
    }

    @Override
    public Collection<TradletGroup> getGroups() {
        List<TradletGroup> result = new ArrayList<>(groupEngines.size());
        for(int i=0;i<groupEngines.size();i++) {
            result.add(groupEngines.get(i).getGroup());
        }
        return result;
    }

    @Override
    public TradletGroup getGroup(String groupId) {
        for(int i=0;i<groupEngines.size();i++) {
            if ( groupEngines.get(i).getGroup().getId().equals(groupId)) {
                return groupEngines.get(i).getGroup();
            }
        }
        return null;
    }

    /**
     * 重新加载交易策略组的配置.
     *
     * @return 返回新增或更新的GroupId
     */
    @Override
    public JsonObject reloadGroups()
    {
        Set<String> newGroupIds = new TreeSet<>(), updatedGroupIds = new TreeSet<>(), deletedGroupIds = new TreeSet<>();
        Map<String, TradletGroupEngine> newGroupEngines = new TreeMap<>();
        //Key: groupId, Value groupConfig Text
        Map<String, TradletGroupTemplate> updatedGroupTemplates = new TreeMap<>();
        Map<String, TradletGroupEngine> currGroupEngines = new LinkedHashMap<>();
        for(TradletGroupEngine groupEngine:groupEngines) {
            currGroupEngines.put(groupEngine.getGroup().getId(), groupEngine);
        }
        Map<String, TradletGroupEngine> allGroupEngines = new LinkedHashMap<>();
        int failedGroups=0;

        //检查配置是否有更新
        Map<String, String> groupConfigs = new HashMap<>();
        for(Map groupElem:(List<Map>)ConfigUtil.getObject(ITEM_TRADLETGROUPS)) {
            String groupId = ConversionUtil.toString(groupElem.get("id"));
            String groupConfig = ConversionUtil.toString( groupElem.get("text") );
            groupConfigs.put(groupId, groupConfig);
            TradletGroupEngine groupEngine = currGroupEngines.remove(groupId);
            if (groupEngine != null && groupEngine.getGroup().getConfig().equals(groupConfig)) {
                //没有变化, 忽略
            } else {
                try {
                    if (groupEngine == null) { // 新增Group
                        TradletGroupImpl group = createGroup(groupElem);
                        groupEngine = new TradletGroupEngine(group);
                        newGroupEngines.put(groupId, groupEngine);
                        newGroupIds.add(groupId);
                    } else { //更新Group
                        updatedGroupTemplates.put(groupId, TradletGroupTemplate.parse(beansContainer, groupEngine.getGroup(), groupConfig));
                        updatedGroupIds.add(groupId);
                    }
                }catch(Throwable t) {
                    logger.error("Create or update group "+groupId+" failed: "+t.toString(), t);
                    failedGroups++;
                }
            }
            if ( groupEngine!=null ) {
                allGroupEngines.put(groupId, groupEngine);
            }
        }

        //检查Tradlet是否有更新
        for(TradletGroupEngine groupEngine:currGroupEngines.values()) {
            String groupId = groupEngine.getGroup().getId();
            try{
                if ( isGroupTradletUpdated(groupEngine.getGroup()) && !updatedGroupTemplates.containsKey(groupId)) {
                    updatedGroupTemplates.put(groupId, TradletGroupTemplate.parse(beansContainer, groupEngine.getGroup(), groupConfigs.get(groupId)));
                    updatedGroupIds.add(groupId);
                }
            }catch(Throwable t) {
                logger.error("Update group "+groupId+" failed: "+t.toString(), t);
                failedGroups++;
            }
        }

        //为更新的策略组发送更新Event
        for(String groupId:updatedGroupTemplates.keySet()) {
            TradletGroupEngine groupEngine = allGroupEngines.get(groupId);
            groupEngine.queueEvent(TradletEvent.EVENT_TYPE_MISC_GROUP_RELOAD, updatedGroupTemplates.get(groupId));
        }
        //currGroupEngine 如果还有值, 是内存中存在但是配置文件已经删除, 需要将状态置为Disabled
        for(TradletGroupEngine deletedGroupEngine: currGroupEngines.values()) {
            deletedGroupEngine.getGroup().setState(TradletGroupState.Disabled);
            deletedGroupEngine.destroy();
            deletedGroupIds.add(deletedGroupEngine.getGroup().getId());
        }
        //为新增策略组创建新的线程
        for(TradletGroupEngine engine:newGroupEngines.values()) {
            try{
                engine.init(beansContainer);
            }catch(Throwable t) {
                logger.error("Init tradlet group "+engine.getGroup().getId()+" failed: "+t, t);
            }
        }
        String message = "Reload "+allGroupEngines.size()+" tradlet groups: "+(allGroupEngines.keySet())+", add: "+newGroupEngines.keySet()+", updated: "+updatedGroupTemplates.keySet()+", removed: "+currGroupEngines.keySet();
        logger.info(message);
        groupEngines = new ArrayList<>(allGroupEngines.values());
        JsonObject result = new JsonObject();
        result.add("new", JsonUtil.object2json(newGroupIds) );
        result.add("updated", JsonUtil.object2json(updatedGroupIds));
        result.add("deleted", JsonUtil.object2json(deletedGroupIds));
        result.addProperty("failedGroups", failedGroups);
        return result;
    }

    /**
     * 返回所有含有交易策略实现接口Tradlet的插件
     */
    public static List<Plugin> filterTradletPlugins(List<Plugin> plugins){
        final List<Plugin> tradletPlugins = new LinkedList<>();
        for(Plugin plugin:plugins) {
            if( plugin.getExposedInterfaces().contains(Tradlet.class.getName())) {
                tradletPlugins.add(plugin);
            }
        }
        return tradletPlugins;
    }

    /**
     * 加载策略实现代码
     */
    public static Map<String, TradletInfo> reloadTradletInfos(Map<String, TradletInfo> allTradletInfos, List<Plugin> tradletPlugins, Set<String> allTradletIds, Set<String> updatedTradletIds, Set<String> updatedPluginIds) {
        HashMap<String, TradletInfo> result = new HashMap<>(allTradletInfos);
        //从更新的Plugin发现Tradlet实现类
        for(Plugin plugin:tradletPlugins) {
            Map<String, Class<Tradlet>> tradletClasses = plugin.getBeanClasses(Tradlet.class);
            for(String tradletId:tradletClasses.keySet()) {
                TradletInfo tradletInfo0 = allTradletInfos.get(tradletId);
                //忽略没有更新的Tradlet
                if ( tradletInfo0!=null && tradletInfo0.getTimestamp()==plugin.getLastModified() ) {
                    continue;
                }
                Class<Tradlet> clazz = tradletClasses.get(tradletId);
                if ( null!=updatedTradletIds ) {
                    updatedTradletIds.add(tradletId);
                }
                if ( null!=updatedPluginIds ) {
                    updatedPluginIds.add(plugin.getId());
                }
                result.put(tradletId, new TradletInfoImpl(tradletId, clazz, plugin, plugin.getLastModified()));
            }
        }
        if ( allTradletIds!=null ) {
            for(TradletInfo tradletInfo:result.values()) {
                allTradletIds.add(tradletInfo.getId());
            }
        }
        return result;
    }

    /**
     * 加载标准策略实现类(不支持重新加载)
     */
    public static Map<String, TradletInfo> loadStandardTradlets(){
        Map<String, Class<Tradlet>> tradletClasses = new HashMap<>();
        for(String tradletClazz : StringUtil.text2lines(ConfigUtil.getString(ITEM_TRADLETS), true, true)) {
            Class<Tradlet> clazz;
            try {
                clazz = (Class<Tradlet>)Class.forName(tradletClazz);
                Discoverable anno = clazz.getAnnotation(Discoverable.class);
                if ( anno!=null ) {
                    tradletClasses.put(anno.purpose(), clazz);
                } else {
                    tradletClasses.put(clazz.getSimpleName(), clazz);
                }
            } catch (Throwable t) {
                logger.error("Load tradlet "+tradletClazz+" failed: "+t.toString(), t);
            }
        }

        Map<String, Class<Tradlet>> discoveredTradlets = DiscoverableRegistry.getConcreteClasses(Tradlet.class);
        if ( discoveredTradlets!=null ) {
            tradletClasses.putAll(discoveredTradlets);
        }

        Map<String, TradletInfo> result = new HashMap<>();
        for(String id:tradletClasses.keySet()) {
            String key = id.toUpperCase();
            if ( !result.containsKey(key) ) {
                result.put(key, new TradletInfoImpl(id, tradletClasses.get(id), null, 0));
            }
        }
        return result;
    }

    private TradletGroupImpl createGroup(Map groupElem) throws AppException
    {
        String groupId = ConversionUtil.toString(groupElem.get("id"));
        String groupConfig = ConversionUtil.toString( groupElem.get("text") );
        TradletGroupImpl group = new TradletGroupImpl(this, beansContainer, groupId);
        group.init(TradletGroupTemplate.parse(beansContainer, group, groupConfig));
        return group;
    }

    /**
     * 检查TradletGroup的Tradlet是否已经更新实现类
     */
    private boolean isGroupTradletUpdated(TradletGroupImpl group) {
        boolean result = false;
        for(TradletHolder tradletHolder: group.getTradletHolders()) {
            TradletInfo tradletInfo = getTradletInfo( tradletHolder.getId() );
            if ( tradletInfo!=null ) {
                result = tradletInfo.getTimestamp()!=tradletHolder.getTradletTimestamp();
            }else {
                result = true;
            }

            if ( result ) {
                break;
            }
        }
        return result;
    }

    private void onPluginChanged(List<Plugin> updatedPlugins) {
        //只关注包含有交易策略的类
        final List<Plugin> tradletPlugins = filterTradletPlugins(updatedPlugins);
        if ( !tradletPlugins.isEmpty() ) {
            executorService.execute(()->{
                Set<String> allTradletIds = new TreeSet<>();
                Set<String> updatedTradletIds = new TreeSet<>();
                Set<String> updatedPluginIds = new TreeSet<>();
                tradletInfos = reloadTradletInfos(tradletInfos, tradletPlugins, allTradletIds, updatedTradletIds, updatedPluginIds);
                logger.info("Total "+allTradletIds.size()+" tradlets, load updated tradlets: "+updatedTradletIds+" from plugins: "+updatedPluginIds);
            });
        }
    }

    /**
     * 派发行情事件到交易组
     */
    private void queueTickEvent(MarketData md) {
        for(int i=0;i<groupEngines.size();i++) {
            TradletGroupEngine groupEngine = groupEngines.get(i);
            if ( groupEngine.getGroup().interestOn(md.instrument) ) {
                groupEngine.queueEvent(TradletEvent.EVENT_TYPE_MD_TICK, md);
            }
        }
    }

    /**
     * 为空闲的TradletGroup派发NoopSecond事件
     */
    private void queueNoopSecondEvent() {
        long curr = System.currentTimeMillis();
        for(int i=0;i<groupEngines.size();i++) {
            TradletGroupEngine groupEngine = groupEngines.get(i);
            if ( (curr-groupEngine.getLastEventTime()) >= TradletEvent.NOOP_TIMEOUT ) {
                groupEngine.queueEvent(TradletEvent.EVENT_TYPE_MISC_NOOP, null);
            }
        }
    }

}
