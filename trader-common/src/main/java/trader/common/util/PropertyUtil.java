package trader.common.util;

import java.util.Properties;

public class PropertyUtil {

    public static Properties extractSubProperties(Properties props, String propPrefix)
    {
        Properties result = new Properties();
        String s = propPrefix+".";
        for(Object k:props.keySet()){
            String key = k.toString();
            String val = props.getProperty(key);
            if ( !key.startsWith(s) )
                continue;
            result.setProperty(key.substring(s.length()), val);
        }
        return result;
    }

    public static String[] extractArrayValue(Properties props, String key){
        String val = props.getProperty(key);
        if ( val==null )
            return null;
        return val.split(",");
    }
}
