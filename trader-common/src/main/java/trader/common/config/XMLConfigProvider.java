package trader.common.config;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemManager;
import org.apache.commons.vfs2.VFS;
import org.jdom2.Attribute;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.input.SAXBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import trader.common.util.StringUtil;

/**
 * XML配置文件加载, 只读配置
 */
public class XMLConfigProvider implements ConfigProvider {
    private final static Logger logger = LoggerFactory.getLogger(XMLConfigProvider.class);

    private FileObject file;
    private long docModified = 0;
    private Document doc;

    public XMLConfigProvider(File file)  throws Exception
    {
        FileSystemManager fsManager = VFS.getManager();
        this.file = fsManager.resolveFile(file.toURI());
    }

    public XMLConfigProvider(FileObject file){
        this.file = file;
    }

    @Override
    public URI getURI() throws Exception
    {
        return file.getURL().toURI();
    }

    @Override
    public List<ConfigItem> getItems(){
        List<ConfigItem> result = new ArrayList<>();
        Element rootElem = doc.getRootElement();
        for(Element elem:rootElem.getChildren()) {
        	result.add(elem2item(elem));
        }
        return result;
    }

    private ConfigItem elem2item(Element elem) {
    	Map<String, String> attrs = new HashMap<>();
    	for(Attribute attr:elem.getAttributes()) {
    		attrs.put(attr.getName(), attr.getValue());
    	}
    	if (!StringUtil.isEmpty(elem.getTextTrim())) {
    	    attrs.put("text", elem.getTextTrim());
    	}
    	ConfigItem result = new ConfigItem(elem.getName(), elem.getTextTrim(), attrs);
    	for(Element child:elem.getChildren()) {
    		result.addChild(elem2item(child));
    	}
    	return result;
    }

    @Override
    public boolean reload() throws Exception
    {
        long t = file.getContent().getLastModifiedTime();
        if ( t== docModified ){
            return false;
        }
        logger.info("Loading config file "+file+", last modified "+t+", prev modified "+docModified);
        SAXBuilder jdomBuilder = new SAXBuilder();
        doc = jdomBuilder.build(file.getContent().getInputStream());
        docModified = t;
        return true;
    }

    @Override
    public void saveItems(Map<String, String> pathValues) throws Exception {
        throw new RuntimeException("Not supported");
    }

}
