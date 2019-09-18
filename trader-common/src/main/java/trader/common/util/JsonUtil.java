package trader.common.util;

import java.io.StringWriter;
import java.lang.reflect.Array;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.ta4j.core.num.Num;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonWriter;

import trader.service.ta.LongNum;

public class JsonUtil {

    public static String getProperty(JsonObject json, String prop, String defaultValue) {
        String result = null;
        if ( json.has(prop)) {
            result = json.get(prop).getAsString();
        }
        if (StringUtil.isEmpty(result)) {
            result = defaultValue;
        }
        return result;
    }

    public static LocalDate getPropertyAsDate(JsonObject json, String prop) {
        LocalDate result = null;
        if ( json.has(prop)) {
            result = DateUtil.str2localdate(json.get(prop).getAsString());
        }
        return result;
    }

    public static LocalDateTime getPropertyAsDateTime(JsonObject json, String prop) {
        LocalDateTime result = null;
        if ( json.has(prop)) {
            result = DateUtil.str2localdatetime(json.get(prop).getAsString());
        }
        return result;
    }

    public static Num getPropertyAsNum(JsonObject json, String prop) {
        Num result = LongNum.ZERO;
        if ( json.has(prop)) {
            result = LongNum.valueOf(json.get(prop).getAsString());
        }
        return result;
    }

    public static JsonArray pricelong2array(long[] v) {
        JsonArray array = new JsonArray(v.length);
        for (int i = 0; i < v.length; i++) {
            array.add(PriceUtil.long2str(v[i]));
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
                int len = Array.getLength(value);
                for (int i = 0; i < len; i++) {
                    array.add(object2json(Array.get(value, i)));
                }
            }
            return array;
        }
        if ( value instanceof JsonElement ) {
            return (JsonElement)value;
        }else if ( value instanceof JsonEnabled ) {
            return ((JsonEnabled)value).toJson();
        }else if (value instanceof Number) {
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
            return (new Gson()).toJsonTree(value);
        }
    }

    public static Object json2value(JsonElement json) {
        Object result = null;
        if ( json.isJsonNull() ) {
            result = null;
        }else if ( json.isJsonObject() ) {
            JsonObject json0 = (JsonObject)json;
            LinkedHashMap<String, Object> map = new LinkedHashMap<>();
            for(String key:json0.keySet()) {
                map.put(key, json2value(json0.get(key)));
            }
            result = map;
        }else if ( json.isJsonArray() ) {
            JsonArray arr = (JsonArray)json;
            ArrayList<Object> list = new ArrayList<>(arr.size());
            for(int i=0;i<arr.size();i++) {
                list.add(json2value(arr.get(i)));
            }
            result = list;
        } else if ( json.isJsonPrimitive() ) {
            JsonPrimitive p = (JsonPrimitive)json;
            if ( p.isBoolean() ) {
                result = p.getAsBoolean();
            }else if ( p.isNumber() ) {
                result = p.getAsDouble();
            }else if ( p.isString()) {
                result = p.getAsString();
            }else {
                result = p.getAsString();
            }
        }
        return result;
    }

    public static String json2str(JsonElement json, Boolean pretty) {
       try {
           StringWriter stringWriter = new StringWriter(1024);
            JsonWriter jsonWriter = new JsonWriter(stringWriter);
            if ( pretty!=null && pretty ) {
                jsonWriter.setIndent("  ");
            }
            jsonWriter.setLenient(true);
            Streams.write(json, jsonWriter);
            return stringWriter.toString();
       }catch(Throwable t) {
           return json.toString();
       }
    }

}
