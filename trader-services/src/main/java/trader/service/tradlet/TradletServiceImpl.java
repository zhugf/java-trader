package trader.service.tradlet;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import trader.common.beans.BeansContainer;
import trader.common.beans.DiscoverableRegistry;
import trader.service.plugin.Plugin;
import trader.service.plugin.PluginListener;
import trader.service.plugin.PluginService;

@Service
public class TradletServiceImpl implements TradletService, PluginListener
{
    private static final Logger logger = LoggerFactory.getLogger(TradletServiceImpl.class);

    private static final String ITEM_TRADLETGROUPS = "/TradletService/tradletGroup[]";

    @Autowired
    private BeansContainer beansContainer;

    @Autowired
    private PluginService pluginService;

    @Autowired
    private ExecutorService executorService;

    @Autowired
    private ScheduledExecutorService scheduledExecutorService;

    private Map<String, TradletInfo> tradletInfos = new HashMap<>();

    private Map<String, TradletGroupThreadedEngine> groupEngines = new HashMap<>();

    @PostConstruct
    public void init() {
        pluginService.registerListener(this);
        tradletInfos = loadStandardTradlets();
        reloadTradletInfos(filterTradletPlugins(pluginService.getAllPlugins()));
        reloadGroups();
        scheduledExecutorService.scheduleAtFixedRate(()->{
            reloadGroups();
        }, 15, 15, TimeUnit.SECONDS);
    }

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
        for(TradletGroupThreadedEngine engine:groupEngines.values()) {
            result.add(engine.getGroup());
        }
        return result;
    }

    @Override
    public TradletGroup getGroup(String groupId) {
        TradletGroupThreadedEngine engine = groupEngines.get(groupId);
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
     * 重新加载交易策略组的配置
     */
    private Set<String> reloadGroups() {
        Set<String> updatedGroupIds = new TreeSet<>();
        List<TradletGroup> currGroups = new ArrayList<>( getGroups() );

        return updatedGroupIds;
    }

}
