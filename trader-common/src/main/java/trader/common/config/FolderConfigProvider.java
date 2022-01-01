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

import trader.common.util.VFSUtil;


/**
 * 基于目录读取所有配置文件
 */
public class FolderConfigProvider implements ConfigProvider {

	private boolean readOnly;
    private FileObject folder;
    private long lastModified=0;

    private String preferPropsFile = "config.properties";
    private List<XMLConfigProvider> xmlConfigs = Collections.emptyList();
    private List<PropertiesConfigProvider> propsConfigs = Collections.emptyList();
    private List<ConfigItem> cachedItems;

    public FolderConfigProvider(File dir) throws Exception
    {
        this(VFSUtil.file2object(dir), false);
    }

    public FolderConfigProvider(File dir, boolean readOnly) throws Exception
    {
        this(VFSUtil.file2object(dir), readOnly);
    }

    public FolderConfigProvider(FileObject folder, boolean readOnly) throws Exception
    {
        this.folder = folder;
        reload();
    }

    /**
     * 设置properties file文件名, 如果当前目录无 properties 文件. 则用使用这个文件名创建一个新的properties文件
     *
     * @param propsFile
     */
    public void setPreferPropsFile(String propsFile) {
        preferPropsFile = propsFile;
    }

    @Override
    public URI getURI() throws Exception {
        return folder.getURL().toURI();
    }

    @Override
    public boolean reload() throws Exception {
        boolean result = false;
        ArrayList<FileObject> children = new ArrayList<>(Arrays.asList(folder.getChildren()));
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
                        return -1*o1.getName().compareTo(o2.getName());
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
                    XMLConfigProvider config = new XMLConfigProvider(fo);
                    int oldIdx = xmlConfigs.indexOf(config);
                    if ( oldIdx>=0 ) {
                        config = xmlConfigs.get(oldIdx);
                    }
                    config.reload();
                	xmlConfigs0.add(config);
                } else if (fname.endsWith(".properties")) {
                    PropertiesConfigProvider config = new PropertiesConfigProvider(fo);
                    int oldIdx = propsConfigs.indexOf(config);
                    if ( oldIdx>=0 ) {
                        config = propsConfigs.get(oldIdx);
                    }
                    config.reload();
                    propsConfigs0.add(config);
                }
            }
            this.xmlConfigs = xmlConfigs0;
            this.propsConfigs = propsConfigs0;
            this.cachedItems = null;
            this.lastModified = lastModified0;
            result=true;
        }
        return result;
    }

    @Override
    public List<ConfigItem> getItems() {
        List<ConfigItem> items = cachedItems ;
        if (null==items) {
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
            this.cachedItems = items;
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
    	//首先找已有的properties文件
        if ( !propsConfigs.isEmpty() ) {
            for(PropertiesConfigProvider config:propsConfigs) {
                if ( config.getURI().toString().endsWith(preferPropsFile)) {
                    propsConfig = config;
                    break;
                }
            }
            if ( null==propsConfig ) {
                propsConfig = propsConfigs.get(0);
            }
        }
        //自动创建一个
        if ( null==propsConfig){
    		propsConfig = new PropertiesConfigProvider( folder.getChild(preferPropsFile) );
    		propsConfigs.add(propsConfig);
    	}
        propsConfig.saveItems(pathValues);
        this.cachedItems = null;
    }

}
