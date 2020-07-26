package trader.common.config;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.NameScope;
import org.apache.commons.vfs2.VFS;


/**
 * 基于目录读取所有配置文件
 */
public class FolderConfigProvider implements ConfigProvider {

	private boolean readOnly;
    private FileObject folder;
    private long lastModified=0;

    private List<XMLConfigProvider> xmlConfigs = Collections.emptyList();
    private List<PropertiesConfigProvider> propsConfigs = Collections.emptyList();

    public FolderConfigProvider(File dir) throws Exception
    {
        this(VFS.getManager().resolveFile(dir.toURI()), false);
    }

    public FolderConfigProvider(File dir, boolean readOnly) throws Exception
    {
        this(VFS.getManager().resolveFile(dir.toURI()), readOnly);
    }

    public FolderConfigProvider(FileObject folder, boolean readOnly) throws Exception
    {
        this.folder = folder;
        reload();
    }

    @Override
    public URI getURI() throws Exception {
        return folder.getURL().toURI();
    }

    @Override
    public boolean reload() throws Exception {
        boolean result = false;
        List<FileObject> children = new ArrayList<>(Arrays.asList(folder.getChildren()));
        long lastModified0 = 0;
        for(FileObject child:children) {
            lastModified0 = Math.max( lastModified0, child.getContent().getLastModifiedTime());
        }
        if ( lastModified0 > lastModified ) {
            //按照名称从大到小排序
            Collections.sort(children, new Comparator<FileObject>() {
                @Override
                public int compare(FileObject o1, FileObject o2) {
                    try{
                        return -1*o1.getName().getBaseName().compareTo(o2.getName().getBaseName());
                    }catch(Throwable t) {
                        throw new RuntimeException(t);
                    }
                }
            });
            List<XMLConfigProvider> xmlConfigs0 = new ArrayList<>(children.size());
            List<PropertiesConfigProvider> propsConfigs0 = new ArrayList<>(children.size());
            for(FileObject fo:children) {
                String fname = fo.getName().getBaseName();
                if ( fname.endsWith(".xml")) {
                	xmlConfigs0.add(new XMLConfigProvider(fo));
                } else if (fname.endsWith(".properties")) {
                	propsConfigs0.add(new PropertiesConfigProvider(fo));
                }
            }
            this.xmlConfigs = xmlConfigs0;
            this.propsConfigs = propsConfigs0;
            result=true;
        }
        return result;
    }

    @Override
    public List<ConfigItem> getItems() {
        List<ConfigItem> items = null;
        for(ConfigProvider provider:xmlConfigs) {
        	if ( null==items ) {
        		items = provider.getItems();
        	} else {
        		items = ConfigItem.merge(items, provider.getItems());
        	}
        }
        for(ConfigProvider provider:propsConfigs) {
        	if ( null==items ) {
        		items = provider.getItems();
        	} else {
        		items = ConfigItem.merge(items, provider.getItems());
        	}
        }
        if ( null==items ) {
        	items = Collections.emptyList();
        }
        return items;
    }

    /**
     * 目前的逻辑:
     * <LI>检查是否有 config.properties文件, 如果有, 保存到这个文件
     * <LI>如果没有, 但是有别的 properties 配置文件, 那么保存到第一个文件
     * <LI>如果不存在 properties文件, 会自动创建一个 config.properties
     */
    @Override
    public void saveItems(Map<String, String> pathValues) throws Exception
    {
    	if ( readOnly ) {
    		throw new RuntimeException("Read only on "+folder.toString());
    	}
    	PropertiesConfigProvider propsConfig = null;
    	if ( propsConfigs.isEmpty() ) {
    		propsConfig = new PropertiesConfigProvider( folder.resolveFile("config.properties", NameScope.CHILD) );
    		propsConfigs.add(propsConfig);
    	} else {
    		for(PropertiesConfigProvider config:propsConfigs) {
    			if ( config.getURI().toString().endsWith("config.properties")) {
    				propsConfig = config;
    				break;
    			}
    		}
    		if ( null==propsConfig ) {
    			propsConfig = propsConfigs.get(0);
    		}
    	}
        propsConfig.saveItems(pathValues);
    }

}
