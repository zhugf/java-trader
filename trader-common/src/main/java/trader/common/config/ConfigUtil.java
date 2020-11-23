package trader.common.config;

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
    public static String getString(String configPath, String defaultValue) {
        String result = ConversionUtil.toString(getObject(configPath));
        if ( StringUtil.isEmpty(result) ) {
        	result = defaultValue;
        }
        if ( !StringUtil.isEmpty(result) ){
            result = substituteStr(result);
        }
        return result;
    }

    public static boolean getBoolean(String configPath, boolean defaultValue){
        Object r = getObject(configPath);
        boolean result = defaultValue;
        if ( !StringUtil.isEmpty(r) ){
        	result = ConversionUtil.toBoolean(r, defaultValue);
        }
        return result;
    }

    public static int getInt(String configPath, int defaultValue){
        Object r = getObject(configPath);
        int result = defaultValue;
        try{
        	if ( !StringUtil.isEmpty(r) ) {
        		result = ConversionUtil.toInt(r);
        	}
        }catch(Throwable t) {}
        return result;
    }

    public static long getLong(String configPath, long defaultValue){
    	Object r = getObject(configPath);
        long result = defaultValue;
        try{
        	if ( !StringUtil.isEmpty(r) ) {
        		result = ConversionUtil.toLong(r);
        	}
        }catch(Throwable t) {}
        return result;
    }

    public static double getDouble(String configPath, double defaultValue){
    	Object r = getObject(configPath);
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
        String val = getString(configPath);
        if ( StringUtil.isEmpty(val) ) {
            return defaultSeconds;
        }
        val = val.trim();
        return ConversionUtil.str2seconds(val);
    }

    public static Object getObject(String configPath){
        return AbstractConfigService.staticGetConfigValue(configPath);
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
    	String str1Sub = getString(str1);
    	if (!StringUtil.isEmpty(str1Sub)) {
    		str1 = str1Sub;
    	}
    	String result = str0+str1+str2;
    	//如果还有第二个要替换的, 递归替换
    	if ( result.indexOf("${")>0 ) {
    		result = substituteStr(result);
    	}
    	return result;
    }


}
