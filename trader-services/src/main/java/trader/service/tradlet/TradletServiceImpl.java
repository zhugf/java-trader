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
import trader.service.tradlet.Tradlet;
import trader.service.tradlet.TradletGroup;
import trader.service.tradlet.TradletMetadata;
import trader.service.tradlet.TradeletService;

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

    private Map<String, TradletMetadataImpl> tactics = new HashMap<>();

    private Map<String, TradletGroupImpl> groups = new HashMap<>();

    @PostConstruct
    public void init() {
        pluginService.registerListener(this);
        tactics = loadStandardTacticMetadatas();
        updateTacticMetadatas(filterTacticPlugins(pluginService.getAllPlugins()));
        scheduledExecutorService.scheduleAtFixedRate(()->{
            reloadGroups();
        }, 15, 15, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void destroy() {

    }

    @Override
    public Collection<TradletMetadata> getTacticMetadatas() {
        return (Collection)tactics.values();
    }

    @Override
    public TradletMetadata getTacticMetadata(String tacticId) {
        return tactics.get(tacticId);
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
            updateTacticMetadatas(tacticPlugins);
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

    private void updateTacticMetadatas(List<Plugin> updatedPlugins) {
        Map<String, TradletMetadataImpl> allTactics = new HashMap<>(tactics);
        Set<String> updatedPluginIds = new TreeSet<>();
        Set<String> updatedTacticIds = new TreeSet<>();
        for(Plugin plugin:updatedPlugins) {
            updatedPluginIds.add(plugin.getId());
        }

        //从已有的策略中删除更新的Plugin
        for(Iterator<Map.Entry<String, TradletMetadataImpl>> it = allTactics.entrySet().iterator(); it.hasNext();) {
            Map.Entry<String, TradletMetadataImpl> entry = it.next();
            Plugin tacticPlugin = entry.getValue().getPlugin();
            if ( tacticPlugin!=null && updatedPluginIds.contains(tacticPlugin.getId())) {
                it.remove();
            }
        }
        //从更新的Plugin发现交易策略
        long timestamp = System.currentTimeMillis();
        for(Plugin plugin:updatedPlugins) {
            Map<String, Class<Tradlet>> tacticClasses = plugin.getBeanClasses(Tradlet.class);
            for(String id:tacticClasses.keySet()) {
                Class<Tradlet> clazz = tacticClasses.get(id);
                updatedTacticIds.add(id);
                allTactics.put(id, new TradletMetadataImpl(id, clazz, plugin, timestamp));
            }
        }
        this.tactics = allTactics;
        String message = "Total tactics "+allTactics.size()+" loaded, "+updatedTacticIds+" updated from plugins: "+updatedPluginIds+" at timestamp "+timestamp;
        if ( updatedTacticIds.isEmpty() ) {
            logger.debug(message);
        }else {
            logger.info(message);
        }
    }

    private Map<String, TradletMetadataImpl> loadStandardTacticMetadatas(){
        Map<String, Class<Tradlet>> tacticClasses = DiscoverableRegistry.getConcreteClasses(Tradlet.class);
        if ( tacticClasses==null ) {
            return Collections.emptyMap();
        }
        Map<String, TradletMetadataImpl> result = new HashMap<>();
        long timestamp = System.currentTimeMillis();
        for(String id:tacticClasses.keySet()) {
            result.put(id, new TradletMetadataImpl(id, tacticClasses.get(id), null, timestamp));
        }
        return result;
    }

    private void reloadGroups() {

    }

}
