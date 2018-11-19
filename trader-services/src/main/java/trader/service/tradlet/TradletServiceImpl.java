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
public class TradletServiceImpl implements TradeletService, PluginListener
{
    private static final Logger logger = LoggerFactory.getLogger(TradletServiceImpl.class);

    @Autowired
    private BeansContainer beansContainer;

    @Autowired
    private PluginService pluginService;

    @Autowired
    private ExecutorService executorService;

    @Autowired
    private ScheduledExecutorService scheduledExecutorService;

    private Map<String, TradletInfo> tradletInfos = new HashMap<>();

    private Map<String, TradletGroupImpl> groups = new HashMap<>();

    @PostConstruct
    public void init() {
        pluginService.registerListener(this);
        tradletInfos = loadStandardTradletFactories();
        updateTradletMetadatas(filterTacticPlugins(pluginService.getAllPlugins()));
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
        return (Collection)groups.values();
    }

    @Override
    public TradletGroup getGroup(String groupId) {
        return groups.get(groupId);
    }

    @Override
    public void onPluginChanged(List<Plugin> updatedPlugins) {
        //只关注包含有交易策略的类
        final List<Plugin> tacticPlugins = filterTacticPlugins(updatedPlugins);
        executorService.execute(()->{
            updateTradletMetadatas(tacticPlugins);
        });
    }

    private List<Plugin> filterTacticPlugins(List<Plugin> plugins){
        final List<Plugin> tacticPlugins = new LinkedList<>();
        for(Plugin plugin:plugins) {
            if( plugin.getExposedInterfaces().contains(Tradlet.class.getName())) {
                tacticPlugins.add(plugin);
            }
        }
        return tacticPlugins;
    }

    private void updateTradletMetadatas(List<Plugin> updatedPlugins) {
        var allTradletInfos = new HashMap<>(tradletInfos);
        Set<String> updatedPluginIds = new TreeSet<>();
        Set<String> updatedTacticIds = new TreeSet<>();
        for(Plugin plugin:updatedPlugins) {
            updatedPluginIds.add(plugin.getId());
        }

        //从已有的策略中删除更新的Plugin
        for(Iterator<Map.Entry<String, TradletInfo>> it = allTradletInfos.entrySet().iterator(); it.hasNext();) {
            Map.Entry<String, TradletInfo> entry = it.next();
            Plugin tacticPlugin = entry.getValue().getPlugin();
            if ( tacticPlugin!=null && updatedPluginIds.contains(tacticPlugin.getId())) {
                it.remove();
            }
        }
        //从更新的Plugin发现Tradlet实现类
        long timestamp = System.currentTimeMillis();
        for(Plugin plugin:updatedPlugins) {
            Map<String, Class<Tradlet>> tacticClasses = plugin.getBeanClasses(Tradlet.class);
            for(String id:tacticClasses.keySet()) {
                Class<Tradlet> clazz = tacticClasses.get(id);
                updatedTacticIds.add(id);
                allTradletInfos.put(id, new TradletInfoImpl(id, clazz, plugin, timestamp));
            }
        }
        this.tradletInfos = allTradletInfos;
        String message = "Total tradlets "+allTradletInfos.size()+" loaded, "+updatedTacticIds+" updated from plugins: "+updatedPluginIds+" at timestamp "+timestamp;
        if ( updatedTacticIds.isEmpty() ) {
            logger.debug(message);
        }else {
            logger.info(message);
        }
    }

    private Map<String, TradletInfo> loadStandardTradletFactories(){
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

    private void reloadGroups() {

    }

}
