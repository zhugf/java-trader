package trader.service.plugin;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import trader.common.beans.BeansContainer;
import trader.common.beans.Discoverable;
import trader.common.beans.Lifecycle;
import trader.common.util.ChildFirstURLClassLoader;
import trader.common.util.FileUtil;
import trader.common.util.JsonUtil;
import trader.common.util.ResourceUtil;
import trader.common.util.StringUtil;

/**
 * 插件实现类.
 * <BR>插件的reload
 */
public class PluginImpl implements Plugin, AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(PluginImpl.class);

    static class ExposedInterface{
        Class clazz;
        Object instance;

        ExposedInterface(Class clazz){
            this.clazz = clazz;
        }

        public boolean isPluginAware() {
            return PluginAware.class.isAssignableFrom(clazz);
        }

        public String getPurpose() {
            Discoverable d = (Discoverable)clazz.getAnnotation(Discoverable.class);
            return d.purpose();
        }

        public Class getInstanceClass() {
            return clazz;
        }

        public Object getInstance(BeansContainer beansContainer) throws Exception
        {
            if ( instance!=null ) {
                return instance;
            }
            instance = clazz.newInstance();
            if(instance instanceof Lifecycle) {
                ((Lifecycle) instance).init(beansContainer);
            }
            return instance;
        }

        public void destroy() {
            if ( instance instanceof Lifecycle) {
                ((Lifecycle)instance).destroy();
            }
            instance = null;
            clazz = null;
        }
    }

    private BeansContainer beansContainer;
    private File pluginDir;
    private Properties props;
    private String id;
    private URLClassLoader classLoader;
    private URL[] classLoaderURLs;

    /**
     * key: interface class, value: concrete classes
     */
    private Map<String, List<ExposedInterface>> exposedClasses = new HashMap<>();

    private long lastModified;

    public PluginImpl(BeansContainer beansContainer, File pluginDir) throws IOException
    {
        this.beansContainer = beansContainer;
        this.pluginDir = pluginDir;
        props = new Properties();
        try(InputStream is = new FileInputStream(new File(pluginDir, FILE_DESCRIPTOR));){
            props.load(is);
        }
        id = props.getProperty(PROP_ID);
        //permanent = "true".equalsIgnoreCase(props.getProperty(PROP_PERMANENT));
        initExposedInterfaces();
        try{
            initClassLoader();
        }catch(IOException ioe) {
            logger.error("Plugin "+getId()+" load classes/resources failed");
        }
        reloadBeans();

        //记录PluginAware类
        Collection<String> pluginAwareClasses = getPluginAwareClasses();
        String msg = "Plugin "+getId()+" is started with PluginAware instances: "+pluginAwareClasses;
        if ( !pluginAwareClasses.isEmpty() ) {
            logger.info(msg);
        }else {
            logger.debug(msg);
        }
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public Set<String> getExposedInterfaces() {
        return exposedClasses.keySet();
    }

    @Override
    public Properties getProperties() {
        return props;
    }

    @Override
    public File getPluginDirectory() {
        return pluginDir;
    }

    @Override
    public ClassLoader getClassLoader() {
        return classLoader;
    }

    @Override
    public URL[] getClassLoaderURLs() {
        return classLoaderURLs;
    }

    @Override
    public List<File> scanFiles(FileFilter filter)
    {
        List<String> ignoredFiles = Arrays.asList( new String[] {FILE_DESCRIPTOR, "classes", "jars", "lib"} );
        List<File> result = new ArrayList<>();

        LinkedList<File> fileQueue = new LinkedList<>();
        fileQueue.addAll( Arrays.asList(pluginDir.listFiles()) );
        while(!fileQueue.isEmpty()) {
            File file = fileQueue.poll();
            if ( null==file || ignoredFiles.contains(file.getName())) {
                continue;
            }
            if ( file.isDirectory() ) {
                File[] files = file.listFiles();
                if ( null!=files ) {
                    fileQueue.addAll( Arrays.asList(files) );
                }
                continue;
            }
            if (filter.accept(file) ) {
                result.add(file);
            }
        }
        return result;
    }

    @Override
    public<T> T getBean(String clazz) {
        List<ExposedInterface> beanClasses = exposedClasses.get(clazz);
        if ( beanClasses==null || beanClasses.isEmpty() ) {
            return null;
        }
        try {
            return (T)beanClasses.get(0).getInstance(beansContainer);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public<T> T getBean(Class<T> clazz) {
        return (T)getBean(clazz.getName());
    }

    @Override
    public<T> T getBean(Class<T> clazz, String purposeOrId) {
        List<ExposedInterface> classes = exposedClasses.get(clazz.getName());
        if ( classes==null || classes.isEmpty() ) {
            return null;
        }
        for(ExposedInterface i:classes) {
            if ( purposeOrId.equals(i.getPurpose())) {
                try {
                    return (T)i.getInstance(beansContainer);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return null;
    }

    @Override
    public <T> Map<String, T> getBeansOfType(Class<T> clazz) {
        List<ExposedInterface> classes = exposedClasses.get(clazz.getName());
        if ( classes==null || classes.isEmpty() ) {
            return Collections.emptyMap();
        }
        Map<String, T> result = new HashMap<>();
        for(ExposedInterface i:classes) {
            try {
                result.put(i.getPurpose(), (T)i.getInstance(beansContainer));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return result;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Map<String, Class> getBeanClasses(String className){
        List<ExposedInterface> classes = exposedClasses.get(className);
        if ( classes==null || classes.isEmpty() ) {
            return Collections.emptyMap();
        }
        Map<String, Class> result = new TreeMap<>();
        for(ExposedInterface i:classes) {
            result.put(i.getPurpose(), i.getInstanceClass());
        }
        return result;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public<T> Map<String, Class<T>> getBeanClasses(Class<T> clazz){
        return (Map)getBeanClasses(clazz.getName());
    }

    public boolean needsReload() {
        long thisModified = getPluginTimestamp(false);
        if ( thisModified>lastModified ) {
            logger.info("Plugin "+getId()+" needs reload, new modified: "+(new Date(thisModified)));
            return true;
        }
        return false;
    }

    @Override
    public long getLastModified() {
        return lastModified;
    }

    /**
     * 尝试更新或则重新加载Jar Beans
     */
    public boolean reload() throws IOException
    {
        List<File> updatedFiles = new ArrayList<>();
        long thisModified = listUpdateFiles(lastModified, updatedFiles);
        if ( logger.isDebugEnabled() ) {
            logger.debug("Total "+updatedFiles.size()+" updated files found since last "+lastModified+", this last modified "+thisModified);
        }
        if ( updatedFiles.isEmpty() ) {
            return false;
        }
        lastModified = thisModified;
        List<String> updatedFileNames = new ArrayList<>(updatedFiles.size());
        boolean jarFileUpdated = false;
        for(File file:updatedFiles) {
            if ( file.getName().toLowerCase().endsWith("jar")) {
                jarFileUpdated = true;
            }
            updatedFileNames.add(file.getName());
        }
        logger.info("Plugin "+getId()+" is updated, "+updatedFileNames.size()+" files were changed: "+updatedFileNames);
        if ( jarFileUpdated ) {
            //重新加载Beans
            reloadBeans();
        }else {
            //当只有配置文件更新时, 需要通知现有的PluginAware.onFileUpdated函数.
            notifyFileUpdated(updatedFiles);
        }
        return true;
    }

    @SuppressWarnings("rawtypes")
    private void reloadBeans() throws IOException
    {
        destroyBeans();
        initExposedInterfaces();
        List<URL> urls = initClassLoader();
        ScanResult scanResult = (new ClassGraph()).ignoreParentClassLoaders().overrideClasspath(urls).enableAnnotationInfo().addClassLoader(getClassLoader()).scan();
        for(ClassInfo classInfo:scanResult.getAllStandardClasses()) {
            if ( classInfo.getAnnotationInfo(Discoverable.class.getName())==null ) {
                continue;
            }
            Class clazz = null;
            try{
                clazz = classInfo.loadClass();
            }catch(Throwable t) {}
            if ( null==clazz || clazz.getClassLoader()!=this.getClassLoader()) {
                continue;
            }
            boolean hasDefaultConstructor = false;
            for(Constructor c: clazz.getConstructors()){
                if ( c.getParameterCount()==0 ){
                    hasDefaultConstructor = true;
                }
            }
            if ( !hasDefaultConstructor ){
                continue;
            }
            Discoverable d = null;
            try {
                d= (Discoverable)clazz.getAnnotation(Discoverable.class);
            }catch(Throwable t) {}
            if ( d==null ) {
                continue;
            }
            List<ExposedInterface> list = exposedClasses.get( d.interfaceClass().getName() );
            if (list==null) {
                list = new ArrayList<>();
                exposedClasses.put(d.interfaceClass().getName(), list);
            }
            ExposedInterface i = new ExposedInterface(clazz);
            list.add(i);
            if ( i.isPluginAware() ) {
                try {
                    i.getInstance(beansContainer);
                }catch(Throwable t) {
                    logger.error("Plugin "+getId()+" instant plugin aware class "+clazz+" failed", t);
                }
            }
        }
        if ( logger.isInfoEnabled() ) {
            String msg = "Plugin "+getId()+" load "+exposedClasses.size()+" exposed interfaces: "+exposedClasses.keySet();
            if ( exposedClasses.size()>0 ) {
                logger.info(msg);
            }else {
                logger.debug(msg);
            }
        }
    }

    /**
     * 解析plugin properties文件中的exposedInterfaces属性
     */
    private void initExposedInterfaces() {
        Map<String, List<ExposedInterface>> exposedClasses = new HashMap<>();
        if ( props.containsKey(PROP_EXPOSED_INTERFACES)) {
            String interfaces[] = StringUtil.split(props.getProperty(PROP_EXPOSED_INTERFACES), "\\s*(,|;)\\s*");
            for(String interfaceClass:interfaces) {
                exposedClasses.put(interfaceClass, new ArrayList<>());
            }
        }
        this.exposedClasses = exposedClasses;
    }

    private List<URL> initClassLoader() throws IOException
    {
        List<URL> urls = new ArrayList<>();
        {
            File classesDir = new File(pluginDir, "classes");
            if ( classesDir.exists() && classesDir.isDirectory() ) {
                urls.add(classesDir.toURI().toURL());
            }
        }
        {
            File resourcesDir = new File(pluginDir, "resources");
            if ( resourcesDir.exists() && resourcesDir.isDirectory() ) {
                urls.add(resourcesDir.toURI().toURL());
            }
        }
        {
            File libDir = new File(pluginDir, "lib");
            if ( libDir.exists() && libDir.isDirectory() ) {
                for(File jarFile: libDir.listFiles((File f)->{ return f.isFile() && f.getName().endsWith("jar"); })) {
                    urls.add(jarFile.toURI().toURL());
                }
            }
        }
        {
            File jarsDir = new File(pluginDir, "jars");
            if ( jarsDir.exists() && jarsDir.isDirectory() ) {
                for(File jarFile: jarsDir.listFiles((File f)->{ return f.isFile() && f.getName().endsWith("jar"); })) {
                    urls.add(jarFile.toURI().toURL());
                }
            }
        }
        this.classLoaderURLs =  urls.toArray(new URL[urls.size()]);
        classLoader = new ChildFirstURLClassLoader(classLoaderURLs, getClass().getClassLoader());
        return urls;
    }

    /**
     * 列出从上次更新后的新改变的文件
     */
    private long listUpdateFiles(long timestamp, List<File> updateFiles){
        final AtomicLong maxTimestamp = new AtomicLong();
        List<File> files = FileUtil.listAllFiles(pluginDir, (File file)->{
            if ( file.lastModified()>timestamp ) {
                long timetsamp0 = maxTimestamp.get();
                timetsamp0 = Math.max(timetsamp0, file.lastModified());
                maxTimestamp.set(timetsamp0);
                return true;
            }
            return false;
        });
        updateFiles.addAll(files);

        return maxTimestamp.get();
    }

    /**
     * 插件目录的最新文件的修改记录, 只统计 jar/class 文件
     */
    private long getPluginTimestamp(boolean jarOnly) {
        AtomicLong result = new AtomicLong();

        FileUtil.listAllFiles(pluginDir, (File file)->{
            String fname = file.getName();
            if ( jarOnly ) {
                if ( !(fname.endsWith(".jar") || fname.endsWith(".class")) ){
                    return false;
                }
            }
            long lastModified = file.lastModified();
            if ( result.get()<lastModified ) {
                result.set(lastModified);
            }
            return false;
        });

        return result.get();
    }

    @Override
    public void close()
    {
        destroyBeans();
        classLoader = null;
        props = null;

        Collection<String> pluginAwareClasses = getPluginAwareClasses();
        String msg = "Plugin "+getId()+" is closed with PluginAware instances: "+pluginAwareClasses;
        if ( !pluginAwareClasses.isEmpty()) {
            logger.info(msg);
        } else {
            logger.debug(msg);
        }
    }

    @Override
    public boolean isClosed() {
        return null==props;
    }

    @Override
    public String toString() {
        return toJson().toString();
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", getId());
        json.add("properties", JsonUtil.object2json(getProperties()));
        json.addProperty("directory", getPluginDirectory().toString());
        json.add("exposedInterfaces", JsonUtil.object2json(getExposedInterfaces()) );
        json.addProperty("timestamp", lastModified);
        if( classLoader instanceof URLClassLoader ) {
            json.add("classLoaderURLs", JsonUtil.object2json(classLoader.getURLs()));
        }
        File pluginParams = ResourceUtil.loadLocalizedFile(pluginDir, "pluginParams.xml", null);
        if ( pluginParams!=null ) {
            try {
                json.addProperty("pluginParams", FileUtil.read(pluginParams));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return json;
    }

    private void destroyBeans() {
        if ( null!=exposedClasses ) {
            try {
                for(List<ExposedInterface> eis:exposedClasses.values()) {
                    for(ExposedInterface ei:eis) {
                        ei.destroy();
                    }
                }
            }catch(Throwable t) {}
        }
        exposedClasses = new HashMap<>();
    }

    /**
     * 通知PluginAware接口, 有文件更新
     */
    private void notifyFileUpdated(List<File> files) {
        if ( exposedClasses==null ) {
            return;
        }
        try {
            for(List<ExposedInterface> eis:exposedClasses.values()) {
                for(ExposedInterface ei:eis) {
                    if ( !ei.isPluginAware() ) {
                        continue;
                    }
                    PluginAware pa = (PluginAware)ei.getInstance(beansContainer);
                    pa.onFileUpdated(files);
                }
            }
        }catch(Throwable t) {}
    }

    private Collection<String> getPluginAwareClasses(){
        TreeSet<String> pluginAwareClasses = new TreeSet<>();
        for(List<ExposedInterface> itfs:exposedClasses.values()) {
            for(ExposedInterface itf:itfs) {
                if ( itf.isPluginAware()) {
                    pluginAwareClasses.add(itf.clazz.getName());
                }
            }
        }
        return pluginAwareClasses;
    }

}
