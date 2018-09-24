package trader.common.util;

import java.io.*;
import java.net.URL;
import java.util.*;

public class ResourceUtil {

    public static InputStream load(String... paths) throws IOException
    {
        InputStream is = null;
        for(String path: paths){
            File f = new File(path);
            if ( f.exists() ){
                is = new FileInputStream(f);
                break;
            }
            is = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
            if ( is!=null ){
                break;
            }
            is = Thread.currentThread().getContextClassLoader().getResourceAsStream("/"+path);
            if ( is!=null ){
                break;
            }
            is = ClassLoader.getSystemResourceAsStream(path);
            if ( is!=null ){
                break;
            }
            is = ClassLoader.getSystemResourceAsStream("/"+path);
            if ( is!=null ){
                break;
            }
            is = ResourceUtil.class.getClassLoader().getResourceAsStream(path);
            if ( is!=null ){
                break;
            }
            is = ResourceUtil.class.getClassLoader().getResourceAsStream("/"+path);
            if ( is!=null ){
                break;
            }
        }
        return is;
    }

    public static File detectJarFile(Class clazz)
    {
        String fullClassFile = clazz.getName().replaceAll("\\.", "/")+".class";
        String jarFile = clazz.getClassLoader().getResource( fullClassFile ).getPath();
        if ( jarFile.indexOf("!")>0){
            jarFile = jarFile.substring(0, jarFile.indexOf('!'));
        }
        if ( jarFile.endsWith(fullClassFile)){
            jarFile = jarFile.substring(0, jarFile.length()-fullClassFile.length());
        }
        if ( jarFile.startsWith("file:///")){
            jarFile = jarFile.substring("file:///".length());
        }else if (jarFile.startsWith("file:")){
            jarFile = jarFile.substring(5);
        }
        return new File(jarFile);
    }

    public static File getFile(String resName, Class clazz )
    {
        URL url = null;
        if ( clazz!= null ){
            url = clazz.getClassLoader().getResource(resName);
        }
        if ( url==null && clazz!=null ){
            url = clazz.getClassLoader().getResource("/"+resName);
        }
        if ( url==null ){
            url = ClassLoader.getSystemResource(resName);
        }
        if ( url==null ){
            url = ClassLoader.getSystemResource("/"+resName);
        }
        if ( url==null ){
            url = Thread.currentThread().getContextClassLoader().getResource(resName);
        }
        if ( url== null ){
            throw new RuntimeException("Resource \""+resName+"\" is not found with class "+clazz);
        }
        String file = url.getFile();
        return new File(file);
    }

    /**
     * 列出本地化文件的名称
     */
    public static List<String> listLocalizedFiles(String messageFile, Locale locale) {
        int dot = messageFile.lastIndexOf('.');
        String messageFileMain = null;
        String messageFileExt = null;
        if ( dot>0) {
            messageFileMain = messageFile.substring(0, dot);
            messageFileExt = messageFile.substring(dot);
        }else {
            messageFileMain = messageFile;
            messageFileExt = "";
        }
        List<String> paths = new ArrayList<>();

        if ( locale!=null ) {
            String cn = locale.getCountry();
            String cn3 = locale.getISO3Country();
            String ln = locale.getLanguage();

            //add file_message_zh_CN.properties
            if ( !StringUtil.isEmpty(cn) ) {
                paths.add( messageFileMain+"_"+ln+"_"+cn+messageFileExt);
            }
            if ( !StringUtil.isEmpty(cn3) ) {
                paths.add( messageFileMain+"_"+ln+"_"+cn3+messageFileExt);
            }
            //add files_message_zh.properties
            paths.add( messageFileMain+"_"+ln+messageFileExt);
        }
        paths.add( messageFile);
        return paths;
    }

    /**
     * 根据locale信息加载, 本地化文件
     */
    public static File loadLocalizedFile(File directory, String messageFile, Locale locale) {
        List<String> paths = listLocalizedFiles(messageFile, locale);
        for(String path:paths) {
            File f =new File(directory, path);
            if ( f.exists() ) {
                return f;
            }
        }
        return null;
    }

    /**
     * 根据某个类的相对路径,加载本地化文件, 顺序依次是:
     * <LI>fileName_zh_CN.ext
     * <LI>fileName_zh.ext
     * <LI>fileName.ext
     */
    public static String loadLocalizedResource(ClassLoader classLoader, String packageName, String messageFile, Locale locale)
    {
        if ( !StringUtil.isEmpty(packageName) ) {
            packageName = packageName.replace('.', '/');
        }else {
            packageName = "";
        }
        List<ClassLoader> classLoaders = new ArrayList<>();
        if ( classLoader!=null) {
            classLoaders.add(classLoader);
        }
        classLoaders.add(Thread.currentThread().getContextClassLoader());
        classLoaders.add(ResourceUtil.class.getClassLoader());
        classLoaders.add(ClassLoader.getSystemClassLoader());

        List<String> paths = new ArrayList<>(20);
        for(String path:listLocalizedFiles(messageFile, locale)) {
            paths.add(packageName+"/"+path);
            paths.add("/"+packageName+"/"+path);
        }
        InputStream is = null;
        for(String path:paths) {
            for(ClassLoader cl:classLoaders) {
                is = cl.getResourceAsStream(path);
                if (is!=null ) {
                    break;
                }
            }
            if ( is!=null ) {
                break;
            }
        }
        if ( is!=null ) {
            try {
                return FileUtil.read(is, "UTF-8");
            } catch (IOException e) {}
        }
        return null;
    }

    public static List<URL> loadResources(String resource, ClassLoader[] classLoaders) throws IOException
    {
        List<URL> urls = new ArrayList<>();
        for(ClassLoader classLoader:classLoaders){
            Enumeration<URL> urlElem = classLoader.getResources(resource);
            while(urlElem.hasMoreElements()){
                URL url = urlElem.nextElement();
                if ( !urls.contains(url)){
                    urls.add(url);
                }
            }
            urlElem = classLoader.getResources("/"+resource);
            while(urlElem.hasMoreElements()){
                URL url = urlElem.nextElement();
                if ( !urls.contains(url)){
                    urls.add(url);
                }
            }
        }
        return urls;
    }

    public static URL loadResource(String resource, ClassLoader classLoaders[]) throws IOException
    {
        List<URL> urls = loadResources(resource, classLoaders);
        if ( urls.size()>0 ){
            return urls.get(0);
        }
        return null;
    }
}
