package trader.service.tactic;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;

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
public class TacticServiceImpl implements TacticService, PluginListener
{
    private static final Logger logger = LoggerFactory.getLogger(TacticServiceImpl.class);

    @Autowired
    private BeansContainer beansContainer;

    @Autowired
    private PluginService pluginService;

    @Autowired
    private ExecutorService executorService;

    @Autowired
    private ScheduledExecutorService scheduledExecutorService;

    private Map<String, TacticMetadataImpl> tactics = new HashMap<>();

    private Map<String, TacticGroupImpl> groups = new HashMap<>();

    @PostConstruct
    public void init() {
        pluginService.registerListener(this);
        tactics = loadStandardTacticMetadatas();
        updateTacticMetadatas(filterTacticPlugins(pluginService.getAllPlugins()));
        scheduledExecutorService.scheduleAtFixedRate(()->{
            reloadGroups();
        }, 15, 15, TimeUnit.SECONDS);
    }

    @Override
    public Collection<TacticMetadata> getTacticMetadatas() {
        return (Collection)tactics.values();
    }

    @Override
    public TacticMetadata getTacticMetadata(String tacticId) {
        return tactics.get(tacticId);
    }

    @Override
    public Collection<TacticGroup> getGroups() {
        return (Collection)groups.values();
    }

    @Override
    public TacticGroup getGroup(String groupId) {
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
            if( plugin.getExposedInterfaces().contains(Tactic.class.getName())) {
                tacticPlugins.add(plugin);
            }
        }
        return tacticPlugins;
    }

    private void updateTacticMetadatas(List<Plugin> updatedPlugins) {
        Map<String, TacticMetadataImpl> allTactics = new HashMap<>(tactics);
        Set<String> updatedPluginIds = new TreeSet<>();
        Set<String> updatedTacticIds = new TreeSet<>();
        for(Plugin plugin:updatedPlugins) {
            updatedPluginIds.add(plugin.getId());
        }

        //从已有的策略中删除更新的Plugin
        for(Iterator<Map.Entry<String, TacticMetadataImpl>> it = allTactics.entrySet().iterator(); it.hasNext();) {
            Map.Entry<String, TacticMetadataImpl> entry = it.next();
            Plugin tacticPlugin = entry.getValue().getPlugin();
            if ( tacticPlugin!=null && updatedPluginIds.contains(tacticPlugin.getId())) {
                it.remove();
            }
        }
        //从更新的Plugin发现交易策略
        long timestamp = System.currentTimeMillis();
        for(Plugin plugin:updatedPlugins) {
            Map<String, Class<Tactic>> tacticClasses = plugin.getBeanClasses(Tactic.class);
            for(String id:tacticClasses.keySet()) {
                Class<Tactic> clazz = tacticClasses.get(id);
                updatedTacticIds.add(id);
                allTactics.put(id, new TacticMetadataImpl(id, clazz, plugin, timestamp));
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

    private Map<String, TacticMetadataImpl> loadStandardTacticMetadatas(){
        Map<String, Class<Tactic>> tacticClasses = DiscoverableRegistry.getConcreteClasses(Tactic.class);
        Map<String, TacticMetadataImpl> result = new HashMap<>();
        long timestamp = System.currentTimeMillis();
        for(String id:tacticClasses.keySet()) {
            result.put(id, new TacticMetadataImpl(id, tacticClasses.get(id), null, timestamp));
        }
        return result;
    }

    private void reloadGroups() {

    }

}
