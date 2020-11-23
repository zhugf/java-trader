package trader.common.config;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.vfs2.FileObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import trader.common.util.FileUtil;
import trader.common.util.StringUtil;

/**
 * properties配置实现, 可读可写配置.
 */
public class PropertiesConfigProvider implements ConfigProvider {
    private final static Logger logger = LoggerFactory.getLogger(PropertiesConfigProvider.class);

    private FileObject file;
    private List<String> lines;
    private long docModified = 0;

    public PropertiesConfigProvider(File file) throws IOException
    {
        this.file = FileUtil.vfsFromFile(file);
    }

    public PropertiesConfigProvider(FileObject file){
        this.file = file;
    }

    @Override
    public URI getURI() throws Exception
    {
        return file.getURL().toURI();
    }

    @Override
    public boolean reload() throws Exception {
        long lastModified = file.getContent().getLastModifiedTime();
        if ( lastModified== docModified ){
            return false;
        }
        logger.info("Loading config file "+file+", last modified "+lastModified+", prev modified "+docModified);
        try(InputStream is=file.getContent().getInputStream();){
        	String text = FileUtil.read(is, null);
        	lines = StringUtil.text2lines(text, true, true);
        }
        docModified = lastModified;
        return true;
    }

    @Override
    public List<ConfigItem> getItems(){
    	List<ConfigItem> result = new ArrayList<>();
    	for(String line:lines) {
    		if ( line.trim().length()==0 || line.startsWith("#")) {
    			continue;
    		}
    		int kvIdx = line.indexOf('=');
    		if ( kvIdx<0 ) {
    			continue;
    		}
    		String key = line.substring(0, kvIdx);
    		String val = line.substring(kvIdx+1);

    		ConfigItem.buildItem(result, key, val);
    	}
    	return result;
    }

    /**
     * 合并存储
     */
    @Override
    public void saveItems(Map<String, String> pathValues) throws Exception
    {
    	List<String> newLines = new ArrayList<>();
    	for(String line:lines) {
    		if ( line.trim().length()==0 || line.startsWith("#")) {
    			newLines.add(line);
    			continue;
    		}
    		int kvIdx = line.indexOf('=');
    		if ( kvIdx<0 ) {
    			newLines.add(line);
    			continue;
    		}
    		String key = line.substring(0, kvIdx);
    		String val = line.substring(kvIdx+1);
    		String parts[] = StringUtil.split(key, "/|\\.");
    		String val2 = removePathValue(pathValues, parts);
    		if ( null!=val2 ) {
    			line = key+"="+val2;
    		}
    		newLines.add(line);
    	}
    	for(String path:pathValues.keySet()) {
    		newLines.add(path+"="+pathValues.get(path));
    	}

        String text = StringUtil.lines2text(newLines);
        try(OutputStream fos = file.getContent().getOutputStream(false); BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(fos, StringUtil.UTF8));){
    		writer.write(text);
    	}
    }

    private String removePathValue(Map<String, String> pathValues, String parts[]) {
    	String result = null;
    	for(String key:pathValues.keySet()) {
    		String kparts[] = StringUtil.split(key, ConfigItem.PATTERN_KEY_SPLIT);
			boolean partsEquals = true;
    		if ( kparts.length==parts.length ) {
    			for(int i=0;i<kparts.length;i++) {
    				partsEquals = StringUtil.equals(kparts[0], parts[0]);
    				if ( !partsEquals) {
    					break;
    				}
    			}
    		} else {
    			partsEquals = false;
    		}
    		if ( partsEquals ) {
    			result = pathValues.remove(key);
    			break;
    		}
    	}
    	return result;
    }

}
