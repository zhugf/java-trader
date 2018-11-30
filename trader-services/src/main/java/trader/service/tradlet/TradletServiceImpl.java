package trader.service.tradlet;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import trader.common.beans.BeansContainer;
import trader.common.config.ConfigUtil;
import trader.common.util.ConversionUtil;
import trader.service.beans.DiscoverableRegistry;
import trader.service.md.MarketData;
import trader.service.md.MarketDataService;
import trader.service.plugin.Plugin;
import trader.service.plugin.PluginListener;
import trader.service.plugin.PluginService;
import trader.service.tradlet.TradletGroup.State;

@Service
public class TradletServiceImpl implements TradletService, PluginListener
{
    private static final Logger logger = LoggerFactory.getLogger(TradletServiceImpl.class);

    static final String ITEM_DISRUPTOR_WAIT_STRATEGY = "/TradletService/disruptor/waitStrategy";
    static final String ITEM_DISRUPTOR_RINGBUFFER_SIZE = "/TradletService/disruptor/ringBufferSize";

    private static final String ITEM_TRADLETGROUPS = "/TradletService/tradletGroup[]";

    @Autowired
    private BeansContainer beansContainer;

    @Autowired
    private MarketDataService mdService;

    @Autowired
    private PluginService pluginService;

    @Autowired
    private ExecutorService executorService;

    @Autowired
    private ScheduledExecutorService scheduledExecutorService;

    private Map<String, TradletInfo> tradletInfos = new HashMap<>();

    private Map<String, TradletGroupEngine> groupEngines = new HashMap<>();

    @Override
    public void init(BeansContainer beansContainer) {
        mdService.addListener((MarketData md)->{
            dispatchTradletGroupMarketData(md);
        });
        pluginService.registerListener(this);
        tradletInfos = loadStandardTradlets();
        reloadTradletInfos(filterTradletPlugins(pluginService.getAllPlugins()));
        reloadGroups();
        scheduledExecutorService.scheduleAtFixedRate(()->{
            reloadGroups();
        }, 15, 15, TimeUnit.SECONDS);
    }

    @Override
    @PreDestroy
    public void destroy() {

    }

    @Override
    public Collection<TradletInfo> getTradletInfos() {
        return tradletInfos.values();
    }

    @Override
    public TradletInfo getTradletInfo(String tacticId) {
        return tradletInfos.get(tacticId);
    }

    @Override
    public Collection<TradletGroup> getGroups() {
        List<TradletGroup> result = new ArrayList<>(groupEngines.size());
        for(TradletGroupEngine engine:groupEngines.values()) {
            result.add(engine.getGroup());
        }
        return result;
    }

    @Override
    public TradletGroup getGroup(String groupId) {
        TradletGroupEngine engine = groupEngines.get(groupId);
        if (engine!=null) {
            return engine.getGroup();
        }
        return null;
    }

    @Override
    public void onPluginChanged(List<Plugin> updatedPlugins) {
        //只关注包含有交易策略的类
        final List<Plugin> tacticPlugins = filterTradletPlugins(updatedPlugins);
        executorService.execute(()->{
            Set<String> updatedTradletIds = reloadTradletInfos(tacticPlugins);
        });
    }

    /**
     * 返回所有含有交易策略实现接口Tradlet的插件
     */
    private List<Plugin> filterTradletPlugins(List<Plugin> plugins){
        final List<Plugin> tradletPlugins = new LinkedList<>();
        for(Plugin plugin:plugins) {
            if( plugin.getExposedInterfaces().contains(Tradlet.class.getName())) {
                tradletPlugins.add(plugin);
            }
        }
        return tradletPlugins;
    }

    /**
     * 尝试策略实现类
     */
    private Set<String> reloadTradletInfos(List<Plugin> updatedPlugins) {
        var allTradletInfos = new HashMap<>(tradletInfos);
        Set<String> updatedPluginIds = new TreeSet<>();
        Set<String> updatedTradletIds = new TreeSet<>();
        for(Plugin plugin:updatedPlugins) {
            updatedPluginIds.add(plugin.getId());
        }

        //从已有的策略中删除更新的Plugin
        for(Iterator<Map.Entry<String, TradletInfo>> it = allTradletInfos.entrySet().iterator(); it.hasNext();) {
            Map.Entry<String, TradletInfo> entry = it.next();
            Plugin tradletPlugin = entry.getValue().getPlugin();
            if ( tradletPlugin!=null && updatedPluginIds.contains(tradletPlugin.getId())) {
                it.remove();
            }
        }
        //从更新的Plugin发现Tradlet实现类
        long timestamp = System.currentTimeMillis();
        for(Plugin plugin:updatedPlugins) {
            Map<String, Class<Tradlet>> tradletClasses = plugin.getBeanClasses(Tradlet.class);
            for(String id:tradletClasses.keySet()) {
                Class<Tradlet> clazz = tradletClasses.get(id);
                updatedTradletIds.add(id);
                allTradletInfos.put(id, new TradletInfoImpl(id, clazz, plugin, timestamp));
            }
        }
        this.tradletInfos = allTradletInfos;
        String message = "Total tradlets "+allTradletInfos.size()+" loaded, "+updatedTradletIds+" updated from plugins: "+updatedPluginIds+" at timestamp "+timestamp;
        if ( updatedTradletIds.isEmpty() ) {
            logger.debug(message);
        }else {
            logger.info(message);
        }

        return updatedTradletIds;
    }

    /**
     * 加载标准策略实现类(不支持重新加载)
     */
    private Map<String, TradletInfo> loadStandardTradlets(){
        Map<String, Class<Tradlet>> tradletClasses = DiscoverableRegistry.getConcreteClasses(Tradlet.class);
        if ( tradletClasses==null ) {
            return Collections.emptyMap();
        }
        Map<String, TradletInfo> result = new HashMap<>();
        long timestamp = System.currentTimeMillis();
        for(String id:tradletClasses.keySet()) {
            result.put(id, new TradletInfoImpl(id, tradletClasses.get(id), null, timestamp));
        }
        return result;
    }

    /**
     * 重新加载交易策略组的配置.
     *
     * @return 返回新增或更新的GroupId
     */
    private Set<String> reloadGroups() {
        Set<String> updatedGroupIds = new TreeSet<>();
        Map<String, TradletGroupEngine> newGroupEngines = new TreeMap<>();
        Map<String, Map> updateGroupEngines = new TreeMap<>();
        Map<String, TradletGroupEngine> currGroupEngines = new TreeMap<>(groupEngines);
        Map<String, TradletGroupEngine> allGroupEngines = new TreeMap<>();
        var tradletGroupElems = (List<Map>)ConfigUtil.getObject(ITEM_TRADLETGROUPS);
        if ( tradletGroupElems!=null ) {
            for(Map groupElem:tradletGroupElems) {
                String groupId = ConversionUtil.toString(groupElem.get("id"));
                TradletGroupEngine groupEngine = currGroupEngines.remove(groupId);
                if ( groupEngine==null ) { //新增Group
                    TradletGroupImpl group = new TradletGroupImpl(beansContainer, groupElem);
                    groupEngine = new TradletGroupEngine(group);
                    newGroupEngines.put(groupId, groupEngine);
                    updatedGroupIds.add(groupId);
                }else {
                    TradletGroupImpl group = groupEngine.getGroup();
                    if ( !groupElem.equals(group.getConfig()) ) { //配置发生变化
                        updateGroupEngines.put(groupId, groupElem);
                        updatedGroupIds.add(groupId);
                    }
                }
                allGroupEngines.put(groupId, groupEngine);
            }
        }
        groupEngines = allGroupEngines;

        //为新增策略组创建新的线程
        for(TradletGroupEngine engine:newGroupEngines.values()) {
            try{
                engine.init(beansContainer);
            }catch(Throwable t) {
                logger.error("Tradlet group "+engine.getGroup().getId()+" init failed", t);
            }
        }
        //currGroupEngine 如果还有值, 是内存中存在但是配置文件已经删除, 需要将状态置为Disabled
        for(TradletGroupEngine deletedGroupEngine: currGroupEngines.values()) {
            deletedGroupEngine.getGroup().setState(State.Disabled);
        }
        //为更新的策略组发送更新Event
        for(String groupId:updateGroupEngines.keySet()) {
            Map groupConfig = updateGroupEngines.get(groupId);
            TradletGroupEngine groupEngine = allGroupEngines.get(groupId);
            groupEngine.queueGroupUpdated(groupConfig);
        }
        String message = "重新加载 "+allGroupEngines.size()+" 交易策略组: "+(allGroupEngines.keySet())+", 增: "+newGroupEngines.keySet()+", 改: "+updateGroupEngines.keySet()+", 删: "+currGroupEngines.keySet();
        if ( newGroupEngines.size()>0 || updatedGroupIds.size()>0 || currGroupEngines.size()>0 ) {
            logger.info(message);
        }else {
            logger.debug(message);
        }
        return updatedGroupIds;
    }

    /**
     * 派发行情事件到交易组
     */
    private void dispatchTradletGroupMarketData(MarketData md) {

    }

}
