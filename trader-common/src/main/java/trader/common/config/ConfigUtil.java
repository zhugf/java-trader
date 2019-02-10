package trader.common.config;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import trader.common.util.ConversionUtil;
import trader.common.util.StringUtil;

/**
 * A simple and plug-able configuration framework.
 */
public class ConfigUtil {
    private final static Logger logger = LoggerFactory.getLogger(ConfigUtil.class);

    /**
     * 根据ConfigPath返回配置值.如果要指定ConfigSource, 需要在ConfigPath值前面加上:分隔的ConfigSource, 例如
     * <BR> ConfigSource:ConfigPath
     *
     * @return null if not found
     */
    public static String getString(String configPath) {
        Object r = getObject(configPath);
        if ( r!=null ){
            return substituteStr(r.toString());
        }
        return null;
    }

    public static<T extends Enum<T>> T getEnum(Class<T> enumClazz, String configPath, T defaultValue){
        T result = ConversionUtil.toEnum(enumClazz, getString(configPath));
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
    public static String getString0(String configPath, String defaultValue) {
        Object r = getObject(configPath);
        if ( r!=null ){
            return substituteStr(r.toString());
        }
        return defaultValue;
    }

    public static boolean getBoolean(String configPath, boolean defaultValue){
        Object r = getObject(configPath);
        if ( r!=null ){
            return "true".equalsIgnoreCase(r.toString().trim()) || "on".equalsIgnoreCase(r.toString().trim());
        }
        return defaultValue;
    }

    public static int getInt(String configPath, int defaultValue){
        Object r = getObject(configPath);
        if( r!=null ){
            return Integer.parseInt(r.toString().trim());
        }
        return defaultValue;
    }

    public static long getLong(String configPath, long defaultValue){
        Object r = getObject(configPath);
        if( r!=null ){
            return Long.parseLong(r.toString().trim());
        }
        return defaultValue;
    }

    public static double getDouble(String configPath, double defaultValue){
        Object r = getObject(configPath);
        if( r!=null ){
            return Double.parseDouble(r.toString().trim());
        }
        return defaultValue;
    }

    /**
     * 返回时间(秒). 它会将:
     * <BR>5m 转换为 300
     * <BR>1h 转换为 3600
     * <BR>5m1s 转换为 301
     */
    public static long getTime(String configPath, int defaultSeconds) {
        String val = getString(configPath);
        if ( StringUtil.isEmpty(val) ) {
            return defaultSeconds;
        }
        val = val.trim();
        return ConversionUtil.str2seconds(val);
    }

    public static String getString(String source, String configPath){
        Object r = getObject(source, configPath);
        if( r!=null ){
            return r.toString();
        }
        return null;
    }

    public static String getString(String source, String configPath, String defaultValue){
        String v = getString(source, configPath);
        if ( v==null ){
            v = defaultValue;
        }
        return v;
    }

    public static boolean getBoolean(String source, String configPath, boolean defaultValue){
        Object r = getObject(source, configPath);
        if ( r!=null ){
            return "true".equalsIgnoreCase(r.toString().trim()) || "on".equalsIgnoreCase(r.toString().trim());
        }
        return defaultValue;
    }

    public static int getInt(String source, String configPath, int defaultValue){
        Object r = getObject(source, configPath);
        if( r!=null ){
            return Integer.parseInt(r.toString().trim());
        }
        return defaultValue;
    }

    public static long getLong(String source, String configPath, long defaultValue){
        Object r = getObject(source, configPath);
        if( r!=null ){
            return Long.parseLong(r.toString().trim());
        }
        return defaultValue;
    }

    public static long getTime(String source, String configPath, int defaultValue, TimeUnit timeUnit) {
        String val = getString(configPath);
        if ( StringUtil.isEmpty(val) ) {
            return defaultValue;
        }
        long seconds = ConversionUtil.str2seconds(val.trim());
        return TimeUnit.SECONDS.convert(seconds, timeUnit);
    }

    public static Object getObject(String source, String configPath){
        ConfigProvider provider = AbstractConfigService.staticGetProvider(source);
        if ( provider==null ) {
            return null;
        }
        Object val = provider.getItem(configPath);
        if ( logger.isTraceEnabled() ){
            logger.trace("Config source "+source+" path "+configPath+" : "+val);
        }
        val = substituteObject(val);
        return val;
    }

    public static Object getObject(String configPath){
        Object val = null;
        if ( configPath.indexOf(":")>0 ) {
            int colonIndex = configPath.indexOf(":");
            String configSource = configPath.substring(0, colonIndex);
            String configPath0 = configPath.substring(colonIndex+1);
            val = getObject(configSource, configPath0);
        }else {
            val = AbstractConfigService.staticGetItem(configPath);
            if ( logger.isTraceEnabled() ){
                logger.trace("Config path "+configPath+" : "+val);
            }
            val = substituteObject(val);
        }
        return val;
    }

    private static Object substituteObject(Object obj) {
        if ( obj==null ) {
            return obj;
        }
        if ( obj instanceof String ) {
            return substituteStr(obj.toString());
        }else if ( obj instanceof Map ) {
            Map map = (Map)obj;
            LinkedHashMap result = new LinkedHashMap<>();
            for(Object key:map.keySet()) {
                Object val = map.get(key);
                result.put(key, substituteObject(val));
            }
            return result;
        }else if ( obj instanceof List ) {
            List list = (List)obj;
            List result = new LinkedList<>();
            for(Object val:list) {
                result.add(substituteObject(val));
            }
            return result;
        }
        return obj;
    }

    /**
     * 使用System Property或Config配置值替换${}的占位符
     */
    private static String substituteStr(String str) {
    	if ( StringUtil.isEmpty(str) ) {
    		return str;
    	}
    	int idxBegin = str.indexOf("${")+2;
    	int idxEnd = str.indexOf("}");
    	if ( idxBegin<0 || idxEnd<0 ) {
    		return str;
    	}
    	String str0 = "", str1="", str2 = "";
    	if ( idxBegin-2 >0 ) {
    		str0 = str.substring(0, idxBegin-2);
    		str1 = str.substring(idxBegin, idxEnd);
    		str2 = str.substring(idxEnd+1);
    	}
        String str1Sub = str1;
    	if ( !StringUtil.isEmpty(System.getProperty(str1))) {
    	    str1Sub = System.getProperty(str1);
    	}else {
    	    str1Sub = getString(str1);
    	}
    	String result = str0+str1Sub+str2;
    	//如果还有第二个要替换的, 递归替换
    	if ( result.indexOf("${")>0 ) {
    		result = substituteStr(result);
    	}
    	return result;
    }


}
