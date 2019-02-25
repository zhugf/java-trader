package trader.service.plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.regex.Pattern;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import trader.common.beans.BeansContainer;
import trader.common.config.ConfigUtil;
import trader.common.util.FileUtil;
import trader.common.util.StringUtil;
import trader.common.util.TraderHomeUtil;

@Service
@SuppressWarnings("unchecked")
public class PluginServiceImpl implements PluginService {
    private static final Logger logger = LoggerFactory.getLogger(PluginServiceImpl.class);

    @Autowired
    private BeansContainer beansContainer;

    @Autowired
    private ExecutorService executorService;

    private List<PluginImpl> plugins = new ArrayList<>();

    private List<PluginListener> listeners = new ArrayList<>();

    private List<File> pluginRootDirs = new ArrayList<>();

    public void setBeansContainer(BeansContainer v) {
        this.beansContainer = v;
    }

    @PostConstruct
    public void init() {
        pluginRootDirs = initPluginRootDirs();
        logger.info("Plugin root dirs: "+pluginRootDirs);
        rescan();
    }

    @PreDestroy
    public void destroy() {
        for(PluginImpl plugin:plugins) {
            plugin.close();
        }
    }

    @Override
    public List<Plugin> getAllPlugins() {
        return Collections.unmodifiableList(plugins);
    }

    @Override
    public Plugin getPlugin(String pluginId) {
        for(Plugin plugin:plugins) {
            if ( plugin.getId().equals(pluginId)) {
                return plugin;
            }
        }
        return null;
    }

    @Override
    public List<Plugin> reload() {
        return (List)rescan();
    }

    /**
     * 重新扫描并发现新的插件
     */
    private List<PluginImpl> rescan() {
        long t0 = System.currentTimeMillis();
        final List<PluginImpl> updatedPlugins = new ArrayList<>();
        try{
            plugins = rescan(plugins, updatedPlugins);
            if ( updatedPlugins.isEmpty() ) {
                return Collections.emptyList();
            }
            TreeSet<String> names = new TreeSet<>();
            for(Plugin p:updatedPlugins) {
                names.add(p.getId());
            }
            logger.info("Discovered plugins: "+names);
            Runnable notify = ()->{
                for(PluginListener listener:listeners) {
                    listener.onPluginChanged((List)updatedPlugins);
                }
            };
            if ( executorService!=null ) {
                executorService.execute(notify);
            }else {
                notify.run();
            }
        }catch(Throwable t) {
            logger.error("rescan plugins failed", t);
        }
        long t1 = System.currentTimeMillis();
        List<String> updatedPluginIds = new ArrayList<>();
        for(Plugin p:updatedPlugins) {
            updatedPluginIds.add(p.getId());
        }
        String message = "Rescan plugins in "+(t1-t0)+" ms, total "+plugins.size()+" , updated: "+updatedPluginIds;
        if ( logger.isInfoEnabled() ) {
            logger.info(message);
        }
        return updatedPlugins;
    }

    @Override
    public List<Plugin> search(String queryExpr) {
        List<Plugin> result = new ArrayList<>();
        //Key: property name, Value: property match expression
        final Map<String, String> exprParts = new LinkedHashMap<>();
        for(String exprPart:queryExpr.split("(,|;)\\s*")) {
            String kv[] = exprPart.trim().split("\\s*=\\s*");
            if ( kv.length<2 ) {
                continue;
            }
            exprParts.put(kv[0].trim(), kv[1].trim());
        }
        for(Plugin plugin:getAllPlugins()) {
            Properties pluginProps = plugin.getProperties();
            boolean include = true;
            for(String exprPartKey:exprParts.keySet()) {
                String pluginPropValue = pluginProps.getProperty(exprPartKey);
                String exprPartValue = exprParts.get(exprPartKey);

                //导出接口特殊对待
                if ( exprPartKey.equals(Plugin.PROP_EXPOSED_INTERFACES)) {
                    if ( !plugin.getExposedInterfaces().contains(exprPartValue) ) {
                        include = false;
                        break;
                    }
                    continue;
                }
                //其它属性当成普通字符串处理
                if (StringUtil.isEmpty(pluginPropValue) || !Pattern.matches(exprPartValue, pluginPropValue)) {
                    include = false;
                    break;
                }
            }
            if ( include ) {
                result.add(plugin);
            }
        }

        //Sort by matching keys
        Collections.sort(result, (Plugin p1, Plugin p2)->{
            Properties props1 = p1.getProperties();
            Properties props2 = p2.getProperties();

            for(String matchKey:exprParts.keySet()) {
                String v1 = props1.getProperty(matchKey);
                String v2 = props2.getProperty(matchKey);
                int r = v1.compareTo(v2);
                if ( r!=0 ) {
                    return r;
                }
            }
            return 0;
        });
        return result;
    }

    @Override
    public void registerListener(PluginListener listener) {
        if ( !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    @Override
    public void deregisterListener(PluginListener listener) {
        listeners.remove(listener);
    }

    private static List<File> initPluginRootDirs(){
        Set<File> dirs = new TreeSet<>();
        //add root plugin dir
        String configPluginsDir= ConfigUtil.getString("/PluginService/pluginDir");
        if ( !StringUtil.isEmpty(configPluginsDir) ) {
            dirs.add(new File(configPluginsDir));
        }
        File plugins = TraderHomeUtil.getDirectory(TraderHomeUtil.DIR_PLUGIN);
        if ( plugins.exists() && plugins.isDirectory() && !dirs.contains(plugins) ) {
            dirs.add(plugins);
        }
        return new ArrayList<>(dirs);
    }

    /**
     * 查找所有的plugin目录
     */
    private static Set<File> discoverAllPluginDirs(List<File> pluginRootDirs)
    {
        Set<File> result = new TreeSet<>();
        LinkedList<File> dirs = new LinkedList<>(pluginRootDirs);
        while(!dirs.isEmpty()) {
            File dir = dirs.poll();
            File pluginProps = new File(dir, Plugin.FILE_DESCRIPTOR);
            if ( pluginProps.exists() ) {
                result.add(dir);
                continue;
            }
            dirs.addAll( FileUtil.listSubDirs(dir) );
        }
        return result;
    }

    /**
     * 定期扫描plugin目录.
     *
     * @return 所有的plugin
     */
    private List<PluginImpl> rescan(List<PluginImpl> existsPlugins, List<PluginImpl> updatedPlugins)
    {
        if (existsPlugins==null) {
            existsPlugins = new ArrayList<>();
        }
        List<PluginImpl> allPlugins = new ArrayList<>();

        Map<File, PluginImpl> lastPlugins = new HashMap<>();
        for(PluginImpl p:existsPlugins) {
            lastPlugins.put(p.getPluginDirectory(), p);
        }

        for(File pluginDir:discoverAllPluginDirs(pluginRootDirs)) {
            PluginImpl plugin = lastPlugins.remove(pluginDir);
            try {
                if ( plugin==null ) {
                    plugin = new PluginImpl(beansContainer, pluginDir);
                    updatedPlugins.add(plugin);
                } else { //updated, need to reload
                    if( plugin.reload()) {
                        updatedPlugins.add(plugin);
                    }
                }
                allPlugins.add(plugin);
            } catch (Throwable e) {
                logger.error("Plugin "+pluginDir+" reload failed", e);
            }
        }

        //关闭已经不存在的Plugin
        if ( !lastPlugins.isEmpty() ) {
            for(PluginImpl plugin:lastPlugins.values()) {
                plugin.close();
            }
        }
        return allPlugins;
    }

    /**
     * 静态加载指定搜索指定接口的实现类
     */
    public static Map<String, Class> staticLoadConcreteClasses(Class intfClass) throws Exception
    {
        Map<String, Class> result = new HashMap<>();
        for(File pluginDir : discoverAllPluginDirs(initPluginRootDirs())) {
            PluginImpl plugin = new PluginImpl(null, pluginDir);
            if ( !plugin.getExposedInterfaces().contains(intfClass.getName()) ) {
                continue;
            }
            result.putAll( plugin.getBeanClasses(intfClass) );
        }
        return result;
    }

}
