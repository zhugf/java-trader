package trader.common.config;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.vfs2.FileObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import trader.common.util.ConversionUtil;
import trader.common.util.FileUtil;
import trader.common.util.StringUtil;

/**
 * 实现了大部分ConfigService代码, 需要由继承并加上Spring Service Annotation
 */
public class AbstractConfigService implements ConfigService {

    private final static Logger logger = LoggerFactory.getLogger(AbstractConfigService.class);

    private final static Pattern PARTY_ARRAY_IDX = Pattern.compile("(.*)\\[(\\d+)\\]");

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
    	String[] parts = StringUtil.split(configPath, "/|\\.");
        Object value = null;
        ConfigItem item = null;
        for(int i=0;i<parts.length;i++) {
        	String part = parts[i];
        	if ( i<parts.length-1 ) {
        		item = resolveItem(item, part);
        		if ( null!=item ) {
        			continue;
        		}
        		break;
        	}
        	//最后一个part可以是数组
            if ( part.endsWith("[]")){
            	part = part.substring(0, part.length()-2);
            	List<ConfigItem> children = item.getItems(part);
            	List<Map> result = new ArrayList<>(children.size());
            	for(ConfigItem child:children) {
                    result.add(child.getAllValue());
            	}
            	value = result;
            	break;
            }
        	ConfigItem lastItem = resolveItem(item, part);
        	if ( lastItem!=null ) {
        		value = lastItem.getValue();
        		break;
        	}
			if ( null!= item ) {
				value = item.getAttr(part);
				break;
			}
        }//end of for parts
		return value;
    }

    private static ConfigItem resolveItem(ConfigItem item, String part) {
    	ConfigItem result = null;
    	if ( null==item ) {
    		result = ConfigItem.getItem(globalItems, part);
    	} else {
    		if ( part.indexOf('#')>0 ) {
            	int idx = part.indexOf('#');
            	String itemId = part.substring(idx+1);
            	part = part.substring(0, idx);
            	List<ConfigItem> children = item.getItems(part);
            	for(ConfigItem child:children) {
            		if ( StringUtil.equals(itemId,child.getAttr("id")) || StringUtil.equals(itemId,child.getAttr("name"))){
            			result = child;
            			break;
            		}
            	}
            } else if ( PARTY_ARRAY_IDX.matcher(part).matches()){
            	//abc[0]格式
            	Matcher m = PARTY_ARRAY_IDX.matcher(part);
            	String part0 = m.group(1);
                int idx = ConversionUtil.toInt(m.group(2), true);
                int partIdx=-1;
                for(ConfigItem child:item.getChildren()) {
                	if ( child.getName().equals(part0)) {
                		partIdx++;
                		if ( partIdx==idx ) {
                			result = child;
                			break;
                		}
                	}
                }
            }else{
            	result = item.getItem(part);
            }
    	}
    	return result;
    }

    static {
    	String files = System.getProperty(SYSPROP_CONFIG_FILES);
    	if (!StringUtil.isEmpty(files)) {
    		for(String url0: StringUtil.split(files, ",|;")) {
				try {
					FileObject file = FileUtil.vfsFromURL(url0);
					if ( file.isFolder() ) {
						staticRegisterProvider(null, new FolderConfigProvider(file, false));
					} else {
						if ( url0.endsWith("xml")) {
							staticRegisterProvider(null, new XMLConfigProvider(file));
						} else {
							staticRegisterProvider(null, new PropertiesConfigProvider(file));
						}
					}
				}catch(Throwable t) {
					logger.error("Register config file "+url0+" failed", t);
				}
    		}
    	}
    }

}
