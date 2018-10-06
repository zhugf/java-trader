package trader.common.util;

import java.util.Properties;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class JsonUtil {

    public static JsonObject props2json(Properties props) {
        JsonObject json = new JsonObject();
        for(Object k:props.keySet()) {
            String key = k.toString();
            String val = props.getProperty(key);
            json.addProperty(key, val);
        }
        return json;
    }

    public static JsonArray doubles2array(double[] v) {
        JsonArray array = new JsonArray(v.length);
        for(int i=0;i<v.length;i++) {
            array.add(v[i]);
        }
        return array;
    }

}
