package trader.common.config;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
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

/**
 * JDOM xml file loader
 *
 */
public class XMLConfigProvider implements ConfigProvider {
    private final static Logger logger = LoggerFactory.getLogger(XMLConfigProvider.class);

    private FileObject file;
    private long docModified = 0;
    private Document doc;

    public XMLConfigProvider(File file) throws Exception
    {
        FileSystemManager fsManager = VFS.getManager();
        this.file = fsManager.resolveFile(file.toURI());
    }

    public XMLConfigProvider(FileObject file){
        this.file = file;
    }

    @Override
    public String getURL() throws IOException
    {
        return file.getURL().toString();
    }

    @Override
    public Map<String, String> getItems(){
        throw new RuntimeException("Not supproted yet");
    }

    @Override
    public Object getItem(String configPath) {
        if ( doc==null ){
            logger.error("XML config file "+file+" is not load");
            return null;
        }
        Object value = null;
        boolean isArray = false;
        if ( configPath.endsWith("[]")){
            configPath = configPath.substring(0, configPath.length()-2);
            isArray = true;
        }
        String[] parts = configPath.split("/|\\.");
        if ( !isArray ){
            value = getItem(parts);
        }else{
            value = getItems(parts);
        }
        if ( value==null && logger.isDebugEnabled()){
            logger.debug("Config path "+configPath+" has no default value");
        }
        return value;
    }

    private String getItem(String[] configParts){
        LinkedList<String> parts = new LinkedList<String>( Arrays.asList(configParts));

        Element elem = doc.getRootElement();
        Attribute attr= null;
        while( elem!=null && !parts.isEmpty() ){
            String part = parts.poll();
            if ( part.length()==0 ){
                continue;
            }
            Element child = getChildElem(elem, part);
            if ( child!=null ){
                elem = child;
                attr = null;
                continue;
            }
            attr = elem.getAttribute(part);
            if ( child==null && attr==null ){
                elem = null;
                break;
            }
            continue;
        }
        if ( attr!=null ){
            return attr.getValue();
        }
        if ( elem!=null ){
            return elem.getText();
        }
        return null;
    }

    private List<Object> getItems(String[] configParts)
    {
        LinkedList<String> parts = new LinkedList<String>( Arrays.asList(configParts));
        Element parentElem = doc.getRootElement();
        while( parentElem!=null && parts.size()>1 ){
            String part = parts.poll();
            if ( part.length()==0 ){
                continue;
            }
            parentElem = getChildElem(parentElem, part);
            if ( parentElem==null ){
                break;
            }
            continue;
        }
        if ( parentElem==null || parts.size()==0 ){
            return null;
        }
        List<Object> result = new LinkedList<>();
        for(Element elem:parentElem.getChildren(parts.poll())){
            Map<String, String> map = new HashMap<>();
            map.put("text", elem.getTextTrim());
            for( Attribute attr:elem.getAttributes()){
                map.put(attr.getName(), attr.getValue());
            }
            result.add(map);
        }
        return result;
    }

    private Element getChildElem(Element parent, String childPart) {
        String childElem=childPart,childId=null;
        int idIndex = childPart.indexOf('#');
        if ( idIndex>0 ) {
            childElem = childPart.substring(0, idIndex);
            childId = childPart.substring(idIndex+1);
        }
        if ( childId==null ) {
            return parent.getChild(childElem);
        }else {
            List<Element> children = parent.getChildren(childElem);
            if ( children!=null ) {
                for(Element child:children) {
                    if ( childId.equals(child.getAttributeValue("id"))) {
                        return child;
                    }
                }
            }
        }
        return null;
    }

    @Override
    public boolean reload() throws Exception
    {
        long fileLastModified = file.getContent().getLastModifiedTime();
        if ( fileLastModified== docModified ){
            return false;
        }
        logger.info("Loading config file "+file+", last modified "+fileLastModified+", prev modified "+docModified);
        SAXBuilder jdomBuilder = new SAXBuilder();
        doc = jdomBuilder.build(file.getContent().getInputStream());
        docModified = fileLastModified;
        return true;
    }

}
