package trader.common.config;

import java.io.File;
import java.io.IOException;
import java.util.*;

import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemManager;
import org.apache.commons.vfs2.VFS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import trader.common.util.FileUtil;

public class PropertiesConfigProvider implements ConfigProvider {
    private final static Logger logger = LoggerFactory.getLogger(PropertiesConfigProvider.class);

    private FileObject file;
    private long docModified = 0;
    private Properties props;

    public PropertiesConfigProvider(File file) throws Exception
    {
        FileSystemManager fsManager = VFS.getManager();
        this.file = fsManager.resolveFile(file.toURI());
    }

    public PropertiesConfigProvider(FileObject file){
        this.file = file;
    }

    @Override
    public String getURL() throws IOException
    {
        return file.getURL().toString();
    }

    @Override
    public Object getItem(String configPath) {
        return props.get(configPath);
    }

    @Override
    public boolean reload() throws Exception {
        long fileLastModified = file.getContent().getLastModifiedTime();

        if ( fileLastModified== docModified ){
            return false;
        }
        logger.info("Loading config file "+file+", last modified "+fileLastModified+", prev modified "+docModified);
        props = FileUtil.readProps(file.getContent().getInputStream());
        docModified = fileLastModified;
        return true;
    }

    @Override
    public Map<String, String> getItems(){
        Map<String,String> m = new HashMap<String, String>();
        for(Enumeration e=props.keys(); e.hasMoreElements();){
            String k = e.nextElement().toString();
            String v = props.getProperty(k);
            m.put(k, v);
        }
        return m;
    }

}
