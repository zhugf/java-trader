package trader.common.config;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.jdom2.Attribute;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import trader.common.util.ConversionUtil;
import trader.common.util.StringUtil;
import trader.common.util.VFSUtil;

/**
 * JDOM xml file loader
 *
 */
public class XMLConfigProvider implements ConfigProvider {
    private final static Logger logger = LoggerFactory.getLogger(XMLConfigProvider.class);

    private Pattern partArrayIdx = Pattern.compile("(.*)\\[(\\d+)\\]");
    private Pattern partArrayAttr = Pattern.compile("(.*)\\[(.*)\\]");
    private FileObject file;
    private long docModified = 0;
    private Document doc;

    public XMLConfigProvider(File file) throws FileSystemException{
        this.file = VFSUtil.file2object(file);
    }
    public XMLConfigProvider(FileObject file){
        this.file = file;
    }

    public boolean equals(Object o) {
        if ( o==null || !(o instanceof XMLConfigProvider)) {
            return false;
        }
        XMLConfigProvider p = (XMLConfigProvider)o;
        return file.equals(p.file);
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
        if ( !StringUtil.isEmpty(elem.getTextTrim())) {
            attrs.put("text", elem.getTextTrim());
        }
        ConfigItem result = new ConfigItem(elem.getName(), elem.getTextTrim(), attrs);
        for(Element child:elem.getChildren()) {
            result.addChild(elem2item(child));
        }
        return result;
    }

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
            Element child = null;
            Matcher m = partArrayIdx.matcher(part);
            if ( m.matches() ) {
                String part0 = m.group(1);
                int idx = ConversionUtil.toInt(m.group(2), true);
                List<Element> children = elem.getChildren(part0);
                if (children.size()>idx) {
                    child = children.get(idx);
                } else {
                    break;
                }
            } else {
                child = elem.getChild(part);
            }
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
            parentElem = parentElem.getChild(part);
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
            result.add(getElemConfig(elem));
        }
        return result;
    }

    private Map<String, Object>  getElemConfig(Element elem){
        Map<String, Object> result = new HashMap<>();
        result.put("text", elem.getTextTrim());
        for( Attribute attr:elem.getAttributes()){
            result.put(attr.getName(), attr.getValue());
        }
        if (elem.getChildren() != null){
            List<Map<String,Object>> childrenList = new ArrayList<>();
            for(Element child:elem.getChildren()){
                childrenList.add(getElemConfig(child));
            }
            result.put("children",childrenList);
        }
        return result;
    }

    @Override
    public boolean reload() throws Exception
    {
        long lastModified = file.getContent().getLastModifiedTime();
        if ( lastModified== docModified ){
            return false;
        }
        logger.debug("Loading config file "+file+", last modified "+lastModified+", prev modified "+docModified);
        SAXBuilder jdomBuilder = new SAXBuilder();
        try(InputStream is=file.getContent().getInputStream();){
            doc = jdomBuilder.build(is);
        }
        if (doc == null){
            throw new RuntimeException("XMLConfigProvider.reload doc is null," + file.toString());
        }
        docModified = lastModified;

        return true;
    }

    @Override
    public void saveItems(Map<String, String> configItems) {
        for(Map.Entry<String,String> entry : configItems.entrySet()){
            String[] parts = StringUtil.split(entry.getKey(),"/|\\.");
            saveItem(parts,entry.getValue());
        }
    }

    private void saveItem(String[] configParts,String value){
        LinkedList<String> parts = new LinkedList<>( Arrays.asList(configParts));
        Element elem = doc.getRootElement();
        Attribute attr= null;
        while( elem!=null && !parts.isEmpty() ){
            String part = parts.poll();
            if ( part.length()==0 ){
                continue;
            }
            Element child = null;
            Matcher m = partArrayAttr.matcher(part);
            if ( m.matches() ) {
                String part0 = m.group(1);
                String attrs = m.group(2);
                boolean find = false;
                if (attrs != null){
                    int i = attrs.indexOf('=');
                    if (i > 0){
                        String name = attrs.substring(0,i);
                        String val = attrs.substring(i+1);
                        List<Element> children = elem.getChildren(part0);
                        if (children != null){
                            for(Element e:children){
                                Attribute attribute = e.getAttribute(name);
                                if ( attribute != null && attribute.getValue() != null
                                 && attribute.getValue().equals(val)){
                                    child = e;
                                    find = true;
                                }
                            }
                        }
                    }
                }
                if (!find){
                    elem = null;
                    break;
                }
            } else {
                child = elem.getChild(part);
            }
            if ( child!=null ){
                elem = child;
                attr = null;
                continue;
            }
            attr = elem.getAttribute(part);
            if ( child==null && attr==null ){
                elem.setAttribute(part,value);
                elem = null;
                break;
            }else if (attr != null && parts.isEmpty()){
                elem = null;
                break;
            }
            continue;
        }
        if ( attr!=null ){
            attr.setValue(value);
        }
        if ( elem!=null ){
            elem.setText(value);
        }
    }

    public void saveXML(String outFile) throws IOException{
        Format format = Format.getPrettyFormat();
        format.setEncoding("UTF-8");
        format.setLineSeparator("\n");
        XMLOutputter out = new XMLOutputter(format);
        out.output(doc,new FileOutputStream(new File(outFile)));
    }

}
