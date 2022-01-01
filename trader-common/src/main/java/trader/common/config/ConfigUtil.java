package trader.common.config;

import java.lang.reflect.Method;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import trader.common.util.ConversionUtil;
import trader.common.util.ScopedURLClassLoader;
import trader.common.util.StringUtil;

/**
 * A simple and plug-able configuration framework.
 */
public class ConfigUtil {
    private final static Logger logger = LoggerFactory.getLogger(ConfigUtil.class);

    private final static StackWalker stackWalker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    /**
     * 根据ConfigPath返回配置值.如果要指定ConfigSource, 需要在ConfigPath值前面加上:分隔的ConfigSource, 例如
     * <BR> ConfigSource:ConfigPath
     *
     * @return null if not found
     */
    public static String getString(String configPath) {
        Object r = getObjectImpl(configPath, stackWalker.getCallerClass());
        String result = null;
        if ( r!=null ){
            result = substituteStr(r.toString());
        }
        return result;
    }

    public static<T extends Enum<T>> T getEnum(Class<T> enumClazz, String configPath, T defaultValue){
        T result = ConversionUtil.toEnum(enumClazz, getObjectImpl(configPath, stackWalker.getCallerClass()));
        if ( result ==null ) {
            return defaultValue;
        }
        return result;
    }

    /**
     * 根据ConfigPath返回配置值
     *
     * @return default value if not found
     */
    public static String getString(String configPath, String defaultValue) {
        Object r = getObjectImpl(configPath, stackWalker.getCallerClass());
        String result = defaultValue;
        if ( r!=null ){
            result = substituteStr(r.toString());
        }
        return result;
    }

    public static boolean getBoolean(String configPath, boolean defaultValue){
        Object r = getObjectImpl(configPath, stackWalker.getCallerClass());
        boolean result = defaultValue;
        if ( !StringUtil.isEmpty(r) ){
        	result = ConversionUtil.toBoolean(r, defaultValue);
        }
        return result;
    }

    public static int getInt(String configPath, int defaultValue){
        Object r = getObjectImpl(configPath, stackWalker.getCallerClass());
        int result = defaultValue;
        try{
        	if ( !StringUtil.isEmpty(r) ) {
        		result = ConversionUtil.toInt(r);
        	}
        }catch(Throwable t) {}
        return result;
    }

    public static long getLong(String configPath, long defaultValue){
        Object r = getObjectImpl(configPath, stackWalker.getCallerClass());
        long result = defaultValue;
        try{
        	if ( !StringUtil.isEmpty(r) ) {
        		result = ConversionUtil.toLong(r);
        	}
        }catch(Throwable t) {}
        return result;
    }

    public static double getDouble(String configPath, double defaultValue){
        Object r = getObjectImpl(configPath, stackWalker.getCallerClass());
        double result = defaultValue;
        try{
        	if ( !StringUtil.isEmpty(r) ) {
        		result = ConversionUtil.toDouble(r);
        	}
        }catch(Throwable t) {}
        return result;
    }

    /**
     * 返回时间(秒). 它会将:
     * <BR>5m 转换为 300
     * <BR>1h 转换为 3600
     * <BR>5m1s 转换为 301
     */
    public static long getTime(String configPath, int defaultSeconds) {
        Object r = getObjectImpl(configPath, stackWalker.getCallerClass());
        if ( StringUtil.isEmpty(r) ) {
            return defaultSeconds;
        }
        return ConversionUtil.str2seconds(StringUtil.trim(ConversionUtil.toString(r)));
    }

    public static Object getObject(String configPath){
        Object r = getObjectImpl(configPath, stackWalker.getCallerClass());
        return r;
    }

    private static Object getObjectImpl(String configPath, Class callerClass) {
        ConfigProvider scopedConfigProvider = null;
        {
            Object scoped = null;
            ClassLoader callerLoader = null;
            if ( null!=callerClass)
                callerLoader = callerClass.getClassLoader();
            if ( null!=callerLoader && callerLoader instanceof ScopedURLClassLoader ) {
                scoped = ((ScopedURLClassLoader)callerLoader).getScope();
            }
            if ( null!=scoped) {
                try{
                    Method getBeanMethod = scoped.getClass().getMethod("getBean", new Class[] {Class.class});
                    scopedConfigProvider = (ConfigProvider)getBeanMethod.invoke(scoped, new Object[] {ConfigProvider.class});
                }catch(Throwable t) {}
            }
        }
        return getObject(configPath, scopedConfigProvider);
    }

    public static Object getObject(String configPath, ConfigProvider scopedConfigProvider) {
        Object r = null;
        if ( null!=scopedConfigProvider ) {
            r = AbstractConfigService.staticGetConfigValue(scopedConfigProvider.getItems(), configPath);
        }
        if ( null==r ) {
            r = AbstractConfigService.staticGetConfigValue(null, configPath);
        }
        return r;
    }

    private static String substituteStr(String str) {
    	if ( StringUtil.isEmpty(str) ) {
    		return str;
    	}
    	int idxBegin = str.indexOf("${");
    	int idxEnd = str.indexOf("}");
    	if ( idxBegin<0 || idxEnd<0 ) {
    		return str;
    	}
    	String str0 = "", str1="", str2 = "";
    	if ( idxBegin >=0 ) {
    		str0 = str.substring(0, idxBegin);
    		str1 = str.substring(idxBegin+2, idxEnd);
    		str2 = str.substring(idxEnd+1);
    	}
    	//尝试使用配置参数或Java System Property替换
    	String str1Sub = ConversionUtil.toString(getObject(str1));
    	if ( StringUtil.isEmpty(str1Sub) && System.getProperty(str1)!=null ) {
    	    str1Sub = System.getProperty(str1);
    	}
    	if (StringUtil.isEmpty(str1Sub))
    	    str1Sub = "";
		str1 = str1Sub;
    	String result = str0+str1+str2;
    	//如果还有第二个要替换的, 递归替换
    	if ( result.indexOf("${")>0 ) {
    		result = substituteStr(result);
    	}
    	return result;
    }


}
