package trader.common.config;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import trader.common.beans.ServiceState;
import trader.common.util.StringUtil;

/**
 * 实现了大部分ConfigService代码, 需要由继承并加上Spring Service Annotation
 */
public class AbstractConfigService implements ConfigService {

    private final static Logger logger = LoggerFactory.getLogger(AbstractConfigService.class);

    /**
     * async reload every 5 seconds( will call File.lastModified() )
     */
    protected final static int CHECK_INTERVAL = 15 * 1000;

    /**
     * Check entry every 60 seconds if no local file
     */
    private final static int ENTRY_FORCE_CHECK_INTERVAL = 60 * 1000;

    protected static class ConfigProviderEntry {
        String source;
        ConfigProvider provider;
        File file;
        long lastFileModified;
        long lastCheckTime;
        WatchKey watchKey;

        ConfigProviderEntry(String source, ConfigProvider provider)
        {
            this.source = source;
            this.provider = provider;
            try {
                String url = provider.getURL();
                if ( url!=null && url.startsWith("file") ){
                    file = new File((new URL(url)).getFile());
                }
            } catch (Exception e) {}
        }

        /**
         * 文件配置快速检查文件修改时间, 其它则每分钟检查一次
         */
        public boolean needReloadCheck() {
            if ( file!=null ) {
                long lastModified = file.lastModified();
                boolean r = lastModified>lastFileModified;
                lastFileModified =lastModified;
                return r;
            }else {
                return (System.currentTimeMillis() - lastCheckTime) >= ENTRY_FORCE_CHECK_INTERVAL;
            }
        }

        public void doReload() throws Exception {
            lastCheckTime = System.currentTimeMillis();
            provider.reload();
        }

        public void updateLastCheckTime() {
            lastCheckTime = System.currentTimeMillis();
        }
    }

    protected static class ConfigListenerEntry {
        String source;
        String[] paths;
        ConfigListener listener;
        private Object[] datas;

        ConfigListenerEntry(String source, String[] paths, ConfigListener listener){
            this.source = source;
            this.paths = paths;
            this.listener = listener;
            this.datas = new Object[paths.length];
        }

        /**
         * Config Source是否已经决定
         */
        boolean isSourceResolved() {
            return source!=null;
        }

        /**
         * 尝试匹配config provider.
         * <BR>只有当source!=null时被调用
         */
        boolean tryMatchProvider(String source, ConfigProvider provider) {
            if ( this.source!=null ) {
                return false;
            }
            boolean resolved = false;
            for(String path:paths) {
                if ( provider.getItem(path) !=null ) {
                    resolved = true;
                    break;
                }
            }
            if (resolved ) {
                this.source = source;
                populatePathDatas(provider);
            }
            return resolved;
        }

        void populatePathDatas(ConfigProvider provider) {
            for (int i = 0; i < paths.length; i++) {
                datas[i] = provider.getItem(paths[i]);
            }
        }

        void notifyListener(ConfigProvider provider, boolean force) {
            if (paths == null) {
                if ( logger.isDebugEnabled() ) {
                    logger.debug("Config provider "+source+" notify listener on null path");
                }
                listener.onConfigReload(source, null, null);
                return;
            }
            if ( force ) {
                for (int i = 0; i < paths.length; i++) {
                    String path = paths[i];
                    listener.onConfigReload(source, path, datas[i]);
                }
            } else {
                for (int i = 0; i < paths.length; i++) {
                    String path = paths[i];
                    Object oldData = null;
                    if ( datas!=null ) {
                        oldData = datas[i];
                    }
                    Object newData = provider.getItem(path);
                    if ( oldData==null && newData==null) {
                        continue;
                    }
                    if ((oldData == null && newData != null) || (oldData != null && newData == null)
                            || (!oldData.equals(newData)))
                    {
                        if ( logger.isDebugEnabled() ) {
                            logger.debug("Config provider "+source+" notify listener on item "+path+" old value: "+oldData+", new value: "+newData);
                        }
                        listener.onConfigReload(source, path, newData);
                    }
                    datas[i] = newData;
                }
            }
        }
    }

    protected static Map<String, ConfigProviderEntry> providers = new HashMap<>();
    protected static List<ConfigListenerEntry> listeners = new LinkedList<ConfigListenerEntry>();
    protected ServiceState state;
    protected WatchService watcher;

    public AbstractConfigService() {
    }

    @Override
    public void sourceChange(String source) {
        for (Map.Entry<String, ConfigProviderEntry> providerEntry : providers.entrySet()) {
            if (source.equalsIgnoreCase(providerEntry.getKey())) {
                doReload(providerEntry.getValue());
            }
        }
    }

    @Override
    public void addListener(String source, String[] paths, ConfigListener listener) {
        ConfigProvider provider = null;
        if ( source==null ) {
            for(int i=0;i<paths.length;i++) {
                String path = paths[i];
                source = staticGetSource(path);
                if  ( path.indexOf(":")>0 ) {
                    path = path.substring(path.indexOf(":")+1);
                }
                if ( source!=null ) {
                    break;
                }
            }
        }
        provider = staticGetProvider(source);
        if ( source!=null && provider==null ) {
            throw new RuntimeException("Config source "+source+" is not found");
        }
        ConfigListenerEntry entry = new ConfigListenerEntry(source, paths, listener);
        if ( provider!=null ) {
            entry.populatePathDatas(provider);
        }
        listeners.add(entry);
    }

    @Override
    public ConfigProvider getProvider(String configSource) {
        ConfigProviderEntry entry = providers.get(configSource);
        if (entry == null) {
            return null;
        }
        return entry.provider;
    }

    @Override
    public List<String> getSources() {
        List<String> result = new ArrayList<>();
        result.addAll(providers.keySet());
        return result;
    }

    @Override
    public void registerProvider(String source, ConfigProvider provider) {
        if (providers.containsKey(source)) {
            throw new RuntimeException("Config source " + source + " is registered");
        }
        providers.put(source, new ConfigProviderEntry(source, provider));
    }

    /**
     * 启动目录查看服务
     */
    protected void startWatcher(ExecutorService executorService) {
        try{
            watcher = FileSystems.getDefault().newWatchService();
        }catch(IOException e) {
            throw new RuntimeException(e);
        }
        for(ConfigProviderEntry providerEntry:providers.values()) {
            File configFile = providerEntry.file;
            if ( configFile!=null && configFile.exists() ) {
                Path configFileDir = configFile.getParentFile().toPath();
                try{
                    providerEntry.watchKey = configFileDir.register(watcher, StandardWatchEventKinds.ENTRY_MODIFY);
                }catch(IOException e) {
                    logger.error("watch on directory "+configFileDir+" failed", e);
                }
            }
        }
        executorService.execute(()->{
            watchThreadFunc();
        });
    }

    private void watchThreadFunc() {
        logger.info("Config watch thread is started");
        while(state!=ServiceState.Stopped) {
            WatchKey watchKey = null;
            try{
                watchKey = watcher.poll(100, TimeUnit.MILLISECONDS);
            }catch(Throwable t) {}
            if ( watchKey==null ) {
                Thread.yield();
                continue;
            }
            ConfigProviderEntry providerEntry = getEntryByFile(watchKey);
            if ( providerEntry!=null ) {
                for(WatchEvent<?> event:watchKey.pollEvents()) {
                    WatchEvent<Path> ev = (WatchEvent<Path>) event;
                    WatchEvent.Kind<?> kind = event.kind();
                    Path filename = ev.context();
                    if (kind == java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY
                    && filename.toString().equals(providerEntry.file.getName())) {
                        doReload(providerEntry);
                    }
                }
            }
            watchKey.reset();
        }
        logger.info("Config watch thread is stopped");
    }

    private static ConfigProviderEntry getEntryByFile(WatchKey watchKey) {
        for(ConfigProviderEntry providerEntry:providers.values()) {
            if ( providerEntry.file!=null && providerEntry.watchKey==watchKey) {
                return providerEntry;
            }
        }
        return null;
    }

    /**
     * 重新加载所有的配置源, 该方法需要被子类主动调用
     */
    public void reloadAll() {
        for (ConfigProviderEntry entry : providers.values()) {
            doReload(entry);
        }
    }

    private synchronized boolean doReload(ConfigProviderEntry providerEntry) {
        boolean needCheck = providerEntry.needReloadCheck();
        if ( logger.isDebugEnabled() ) {
            logger.debug("Config provider "+providerEntry.source+" needs check: "+needCheck);
        }
        if (!needCheck) {
            return false;
        }
        providerEntry.updateLastCheckTime();
        ConfigProvider provider = providerEntry.provider;

        boolean changed = false;
        try {
            changed = provider.reload();
        } catch (Exception e) {
            logger.warn("Reload config " + providerEntry.source + " failed", e);
        }
        if ( logger.isDebugEnabled() ) {
            logger.debug("Config provider "+providerEntry.source+" reload checking result: "+changed);
        }
        if (!changed) {
            return false;
        }

        List<ConfigListenerEntry> forceNotifyListeners = new ArrayList<>();
        List<ConfigListenerEntry> selectedListeners = new ArrayList<>();

        //尝试解决未定ConfigListener Entry
        for (ConfigListenerEntry listenerEntry : listeners) {
            if ( !listenerEntry.isSourceResolved() ) {
                if ( listenerEntry.tryMatchProvider(providerEntry.source, provider) ) {
                    forceNotifyListeners.add(listenerEntry);
                }
            }
        }
        //只找匹配的Config Source Listener
        for (ConfigListenerEntry listenerEntry : listeners) {
            if (StringUtil.equalsIgnoreCase(providerEntry.source, listenerEntry.source)) {
                selectedListeners.add(listenerEntry);
            }
        }
        for (ConfigListenerEntry listenerEntry : selectedListeners) {
            try {
                listenerEntry.notifyListener( provider, forceNotifyListeners.contains(listenerEntry));
            } catch (Throwable e) {
                logger.error("Config " + providerEntry.source + " notify failed on "+listenerEntry.listener, e);
            }
        }
        return true;
    }

    public static String staticGetSource(String configPath) {
        if ( configPath.indexOf(":")>0 ) {
            return configPath.substring(0, configPath.indexOf(":"));
        }
        for(ConfigProviderEntry entry:providers.values()) {
            Object item = entry.provider.getItem(configPath);
            if ( item!=null ) {
                return entry.source;
            }
        }
        return null;
    }

    public static Object staticGetItem(String configPath) {
        for(ConfigProviderEntry entry:providers.values()) {
            Object item = entry.provider.getItem(configPath);
            if ( item!=null ) {
                return item;
            }
        }
        return null;
    }

    public static ConfigProvider staticGetProvider(String source) {
        ConfigProviderEntry entry = providers.get(source);
        if (entry != null) {
            return entry.provider;
        }
        return null;
    }

    public static void staticRegisterProvider(String source, ConfigProvider provider) {
        if (providers.containsKey(source)) {
            logger.warn("Config source " + source + " is registered before");
            return;
        }
        ConfigProviderEntry entry = new ConfigProviderEntry(source, provider);

        try {
            entry.doReload();
        } catch (Exception e) {
            logger.error("Config source " + source + " first reload failed", e);
        }
        providers.put(source, entry);
    }

}
