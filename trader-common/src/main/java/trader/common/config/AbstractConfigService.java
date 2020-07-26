package trader.common.config;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import trader.common.util.StringUtil;

/**
 * 实现了大部分ConfigService代码, 需要由继承并加上Spring Service Annotation
 */
public class AbstractConfigService implements ConfigService {

    private final static Logger logger = LoggerFactory.getLogger(AbstractConfigService.class);

    /**
     * async reload every 10 seconds( will call File.lastModified() )
     */
    protected final static int CHECK_INTERVAL = 10 * 1000;

    /**
     * 配置实现类内部保存
     */
    protected static class ConfigProviderEntry {
        String alias;
        ConfigProvider provider;
        List<ConfigItem> items;
        long lastCheckTime;

        ConfigProviderEntry(String source, ConfigProvider provider) throws Exception
        {
            this.alias = source;
            this.provider = provider;
            reload(true);
        }

        public boolean reload(boolean force) throws Exception
        {
        	boolean result = false;
            lastCheckTime = System.currentTimeMillis();
            result = provider.reload();
            if ( result || force ) {
            	items = provider.getItems();
            	result = true;
            }
            return result;
        }

    }

    /**
     * 配置回调接口
     */
    protected class ConfigListenerEntry {
        private ConfigListener listener;
    	private String[] paths;
        private Object[] pathValues;

        ConfigListenerEntry(String[] paths, ConfigListener listener){
            this.paths = paths;
            this.listener = listener;
            this.pathValues = new Object[paths.length];
            for (int i = 0; i < paths.length; i++) {
                pathValues[i] = getConfigValue(paths[i]);
            }
        }

        void notifyListeners() {
            for (int i = 0; i < paths.length; i++) {
                String path = paths[i];
                Object oldValue = null;
                if ( pathValues!=null ) {
                    oldValue = pathValues[i];
                }
                Object newData = getConfigValue(path);
                if ( oldValue==null && newData==null) {
                    continue;
                }
                if ((oldValue == null && newData != null) || (oldValue != null && newData == null)
                        || (!oldValue.equals(newData)))
                {
                    if ( logger.isDebugEnabled() ) {
                        logger.debug("Config listener notify on item "+path+" old value: "+oldValue+", new value: "+newData);
                    }
                    listener.onConfigChanged(path, newData);
                }
                pathValues[i] = newData;
            }
        }
    }

    protected static Map<URI, ConfigProviderEntry> providers = new LinkedHashMap<>();
    protected static List<ConfigItem> globalItems = new ArrayList<>();
    protected List<ConfigListenerEntry> listeners = new LinkedList<ConfigListenerEntry>();

    public AbstractConfigService() {
    }

    @Override
    public synchronized void reload(String alias) {
    	boolean changed = false;
    	try {
	    	if ( StringUtil.equals("*", alias)) {
	    		for (ConfigProviderEntry provider : providers.values() ) {
	    			changed |= provider.reload(false);
	    		}
	    	} else {
		        for (ConfigProviderEntry provider : providers.values() ) {
		            if ( !StringUtil.isEmpty(provider.alias) && StringUtil.equalsIgnoreCase(provider.alias, alias) ) {
		                changed |= provider.reload(false);
		            }
		        }
	    	}
    	}catch(Throwable t) {
    		logger.error("Reload config failed", t);
    	}
    	if ( changed ) {
    		mergeGlobalItems();
    		for(ConfigListenerEntry listenerEntry:this.listeners) {
    			listenerEntry.notifyListeners();
    		}
    	}
    }

    @Override
    public void addListener(String[] paths, ConfigListener listener) {
    	if ( null==paths || paths.length==0 ) {
    		return;
    	}
        ConfigListenerEntry entry = new ConfigListenerEntry(paths, listener);
        synchronized(listeners){
            listeners.add(entry);
        }
    }

    @Override
    public void removeListener(ConfigListener listener) {
        synchronized(listeners){
            for(Iterator<ConfigListenerEntry> it=listeners.iterator();it.hasNext();) {
                ConfigListenerEntry entry = it.next();
                if ( listener==entry.listener ) {
                    it.remove();
                    break;
                }
            }
        }
    }

    @Override
    public void registerProvider(String alias, ConfigProvider provider)
    {
    	staticRegisterProvider(alias, provider);
    }

	@Override
	public Object getConfigValue(String path) {
        return staticGetConfigValue(path);
	}

    protected void reloadAll() {
    	reload("*");
    }

    private static List<ConfigItem> mergeGlobalItems() {
    	List<ConfigItem> result = null;
    	for(ConfigProviderEntry provider:providers.values()) {
    		if ( null==result ) {
    			result = provider.items;
    		} else {
    			result = ConfigItem.merge(result, provider.items);
    		}
    	}
    	globalItems = result;
    	return result;
    }

    public static void staticRegisterProvider(String alias, ConfigProvider provider) {
    	try{
    		URI url = provider.getURI();
    		for(ConfigProviderEntry entry:providers.values()) {
    		    if ( StringUtil.equals(entry.alias, alias) ){
    		        providers.remove(entry.provider.getURI());
    		        break;
    		    }
    		}
            if (!providers.containsKey(url)) {
	            providers.put(url, new ConfigProviderEntry(alias, provider));
	            mergeGlobalItems();
            } else {
            	logger.info("Config provider for "+url+" is registered already");
            }
    	}catch(Throwable e) {
    		throw new RuntimeException(e);
    	}
    }

    public static Object staticGetConfigValue(String configPath) {
        String[] parts = StringUtil.split(configPath,"/|\\.");
        Object value = null;
        ConfigItem item = ConfigItem.getItem(globalItems, parts[0]);
        if ( null!=item ) {
	        for(int i=1;i<parts.length;i++) {
	        	String part = parts[i];
	        	if ( i!=(parts.length-1)) {
	    			item = item.getItem(part);
	    			if ( null==item ) {
	    				break;
	    			} else {
	    				continue;
	    			}
	        	}
        		//分析属性
        		String attrValue = item.getAttrs().get(part);
        		if ( null!=attrValue ) {
        			value = attrValue;
        			break;
        		}
        		//分析数组
	            boolean isArray = false;
	            if ( part.endsWith("[]")){
	            	part = part.substring(0, part.length()-2);
	                isArray = true;
	            }
	            if ( isArray ) {
	            	List<ConfigItem> children = item.getItems(part);
	            	List<Map> result = new ArrayList<>(children.size());
	            	for(ConfigItem child:children) {
	            		result.add(child.getAttrs());
	            	}
	            	value = result;
	            } else {
	    			item = item.getItem(part);
	    			if ( null!= item ) {
	    				value = item.getValue();
	    			}
	            }
	        }//end of for parts
        }
		return value;
    }

}
