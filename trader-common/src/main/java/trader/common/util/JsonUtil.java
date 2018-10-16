package trader.common.util;

import java.util.Iterator;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public class JsonUtil {

    public static JsonArray pricelong2array(long[] v) {
        JsonArray array = new JsonArray(v.length);
        for (int i = 0; i < v.length; i++) {
            array.add(PriceUtil.long2price(v[i]));
        }
        return array;
    }

    public static JsonElement object2json(Object value) {
        if (value == null) {
            return JsonNull.INSTANCE;
        }
        if (value.getClass().isArray()) {
            JsonArray array = new JsonArray();
            Class compClass = value.getClass().getComponentType();
            if (compClass == int.class) {
                int[] v = (int[]) value;
                for (int i = 0; i < v.length; i++) {
                    array.add(v[i]);
                }
            } else if (compClass == double.class) {
                double[] v = (double[]) value;
                for (int i = 0; i < v.length; i++) {
                    array.add(v[i]);
                }
            } else if (compClass == long.class) {
                long[] v = (long[]) value;
                for (int i = 0; i < v.length; i++) {
                    array.add(v[i]);
                }
            } else if (compClass == boolean.class) {
                boolean[] v = (boolean[]) value;
                for (int i = 0; i < v.length; i++) {
                    array.add(v[i]);
                }
            } else {
                Object[] v = (Object[]) value;
                for (int i = 0; i < v.length; i++) {
                    array.add(object2json(v[i]));
                }
            }
            return array;
        }
        if (value instanceof Number) {
            return new JsonPrimitive((Number) value);
        } else if (value instanceof Boolean) {
            return new JsonPrimitive((Boolean) value);
        } else if (value instanceof Map) {
            JsonObject json = new JsonObject();
            Map map = (Map) value;
            for (Object k : map.keySet()) {
                Object v = map.get(k);
                json.add(k.toString(), object2json(v));
            }
            return json;
        } else if (value instanceof Iterable) {
            JsonArray array = new JsonArray();
            for (Iterator it = ((Iterable) value).iterator(); it.hasNext();) {
                array.add(object2json(it.next()));
            }
            return array;
        } else {
            return new JsonPrimitive(value.toString());
        }
    }

}
