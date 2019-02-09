package trader.common.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ConversionUtil {
    private static Class strings = (new String[] {}).getClass();

    public static boolean isStringArray(Object obj) {
        return strings==obj.getClass();
    }

    public static Object toType(Class targetType, Object o) {
        if ( targetType==int.class || targetType==Integer.class ) {
            return toInt(o, true);
        }else if (targetType==long.class || targetType==Long.class) {
            return toLong(o, true);
        }else if (targetType==double.class||targetType==Double.class) {
            return toDouble(o, true);
        }else if ( targetType==String.class) {
            return toString(o);
        }else if (targetType==boolean.class || targetType==Boolean.class ) {
            return toBoolean(o);
        }else if (targetType==LocalDateTime.class) {
            return obj2datetime(o);
        }else {
            return o;
        }
    }

    public static boolean toBoolean(Object obj) {
        return toBoolean(obj, false);
    }

    public static boolean toBoolean(Object obj, boolean defaultValue) {
        if ( obj==null ) {
            return defaultValue;
        }
        if ( obj instanceof Boolean) {
            return ((Boolean)obj).booleanValue();
        }
        else if ( obj.getClass() == boolean.class ) {
            return ((Boolean)obj).booleanValue();
        }
        String str = obj.toString().trim();
        if (StringUtil.equalsIgnoreCase("true", str) || StringUtil.equalsIgnoreCase("yes", str)) {
            return true;
        }
        if (StringUtil.equalsIgnoreCase("false", str) || StringUtil.equalsIgnoreCase("no", str)) {
            return false;
        }
        return defaultValue;
    }

    public static int toInt(Object obj){
        return toInt(obj, false);
    }

    public static int toInt(Object obj, boolean catchException) {
        if ( obj==null ) {
            return 0;
        }
        if ( obj instanceof Number){
            return ((Number)obj).intValue();
        }
        String str = obj.toString();
        if ( StringUtil.isEmpty(str)) {
            return 0;
        }
        try{
            return Integer.parseInt(str.trim());
        }catch(RuntimeException re) {
            if (catchException) {
                return 0;
            }
            throw re;
        }
    }

    public static long toLong(Object obj) {
        return toLong(obj, false);
    }

    public static long toLong(Object obj, boolean catchException){
        if ( obj==null ) {
            return 0;
        }
        if ( obj instanceof Number){
            return ((Number)obj).longValue();
        }
        String str = obj.toString();
        if ( StringUtil.isEmpty(str)) {
            return 0;
        }
        String temp = str.trim();
        if(StringUtil.isEmpty(temp) || temp.equals("-") || temp.equals("+")) {
            return 0;
        }
        try{
            return Long.parseLong(temp);
        }catch(RuntimeException e) {
            if ( catchException ) {
                return 0;
            }
            throw e;
        }
    }

    public static double toDouble(Object obj) {
        return toDouble(obj, false);
    }

    public static double toDouble(Object obj, boolean catchException) {
        if ( obj==null ) {
            return 0;
        }
        if ( obj instanceof Number){
            return ((Number)obj).doubleValue();
        }
        try{
            return Double.parseDouble(obj.toString().trim());
        }catch(RuntimeException re) {
            if ( catchException ) {
                return Double.NaN;
            }
            throw re;
        }
    }

    public static LocalDateTime obj2datetime(Object value) {
        if ( value==null ) {
            return null;
        }
        if ( value.getClass()==LocalDateTime.class ) {
            return (LocalDateTime)value;
        }else if ( (value instanceof Number)) {
            return DateUtil.long2datetime(((Number)value).longValue());
        }else {
            return LocalDateTime.parse(value.toString());
        }
    }

    public static long str2seconds(String str) {
        if (StringUtil.isEmpty(str)) {
            return 0;
        }
        str = str.toLowerCase();
        int begin = 0;
        int cursor = 1;
        long seconds = 0;
        while (cursor < str.length()) {
            if (str.charAt(cursor) >= 'a' && str.charAt(cursor) <= 'z') {
                cursor++;
                String sstr = str.substring(begin, cursor);
                seconds += str2seconds0(sstr);
                begin = cursor;
            }
            cursor++;
        }
        if (begin < str.length()) {
            seconds += str2seconds0(str.substring(begin));
        }
        return seconds;
    }

    /**
     * Convert 1s, 1 to 1 seconds, 1m to 60 seconds, 1h to 3600 seconds, 1d to ***
     */
    private static long str2seconds0(String str) {
        if (StringUtil.isEmpty(str)) {
            return 0;
        }
        str = str.toLowerCase();
        long unit = 1;
        if ( str.endsWith("s")) {
            str = str.substring(0, str.length()-1);
        }else if ( str.endsWith("m")) {
            unit = 60;
            str = str.substring(0, str.length()-1);
        }else if ( str.endsWith("h")) {
            unit = 60*60;
            str = str.substring(0, str.length()-1);
        }else if ( str.endsWith("d")) {
            unit = 60*60*24;
            str = str.substring(0, str.length()-1);
        } else if (str.endsWith("M")) {
            unit = 30 * 24 * 60 * 60;
            str = str.substring(0, str.length() - 1);
        }
        return ((long)(Double.parseDouble(str))) * unit;
    }

    /**
     * 到时间戳
     */
    public static long toTimestamp(Object field) {
        if ( field==null ) {
            return 0;
        }
        if ( field instanceof Number) { //Epoch Millies
            return ((Number)field).longValue();
        }
        return toInstant(field).toEpochMilli();
    }

    public static Instant toInstant(Object field) {
        if ( field==null ) {
            return null;
        }
        if ( field instanceof Instant) {
            return (Instant)field;
        } else if ( field instanceof Number) { //Epoch Millies
            return Instant.ofEpochMilli(((Number)field).longValue());
        }else {
            String text = field.toString();
            //优先使用时间戳
            try {
                long epochMilli = Long.parseLong(text);
                return Instant.ofEpochMilli(epochMilli);
            }catch(Throwable t) {}
            //使用2018-04-18 00:00:02.444格式解析
            LocalDateTime ldt = DateUtil.str2localdatetime(text);
            if ( ldt!=null ) {
                return ZonedDateTime.of(ldt, DateUtil.getDefaultZoneId()).toInstant();
            }
            //使用2018-04-18格式解析
            LocalDate ld = DateUtil.str2localdate(text);
            if ( ld!=null ) {
                return ZonedDateTime.of(ld.atStartOfDay(), DateUtil.getDefaultZoneId()).toInstant();
            }
            Instant result = null;
            try{
                result = Instant.parse(text);
            }catch(Throwable t) {
                return null;
            }
            //使用Instant解析
            return result;
        }
    }

    public static ZonedDateTime toZonedDateTime(Object field) {
        if ( field==null ) {
            return null;
        }
        if ( field instanceof ZonedDateTime) {
            return (ZonedDateTime)field;
        }
        if ( field instanceof Instant) {
            return ZonedDateTime.ofInstant((Instant)field, DateUtil.getDefaultZoneId());
        }
        if ( field instanceof Number ) {
            Instant instant = toInstant(field);
            return ZonedDateTime.ofInstant(instant, DateUtil.getDefaultZoneId());
        }
        String str = field.toString();
        long longValue = toLong(str);
        if ( longValue!=0 ) {
            Instant instant = toInstant(longValue);
            return ZonedDateTime.ofInstant(instant, DateUtil.getDefaultZoneId());
        }
        LocalDateTime ldt = DateUtil.str2localdatetime(str);
        if ( ldt!=null ) {
            return ZonedDateTime.of(ldt, DateUtil.getDefaultZoneId());
        }
        Instant instant = DateUtil.str2instant(str);
        return ZonedDateTime.ofInstant(instant, DateUtil.getDefaultZoneId());
    }


    public static String toString(Object obj) {
        if(obj==null){
            return "";
        }
        return obj.toString();
    }

    /**
     * 将字符串大小写无关解析为enum
     */
    public static <T extends Enum<T>> T toEnum(Class<T> enumClazz, Object obj)
    {
        String str = ConversionUtil.toString(obj);
        if ( StringUtil.isEmpty(str) || !enumClazz.isEnum() ) {
            return null;
        }
        T[] values = enumClazz.getEnumConstants();
        for(T t:values) {
            if ( StringUtil.equalsIgnoreCase(str, t.name()) ) {
                return t;
            }
        }
        return null;
    }

    /**
     * 将值转换为String[].
     * <BR>如果没有值或输入null, 返回EMPTY_LIST
     */
    public static List<String> toStringList(Object value){
        if ( value==null ) {
            return Collections.EMPTY_LIST;
}
        List<String> result = new ArrayList<>();
        if ( value.getClass().isArray() ) {
            Class compType = value.getClass().getComponentType();
            if ( compType.isPrimitive() ) {
                throw new RuntimeException("Primitive array value is not supported");
            }
            for(Object elem:(Object[])value) {
                if( elem!=null ) {
                    result.add(elem.toString());
                }
            }
        }else if ( value instanceof Iterable ) {
            for( Object elem: ((Iterable)value)) {
                if( elem!=null ) {
                    result.add(elem.toString());
                }
            }
        }else {
            result.add(value.toString());
        }

        return result;
    }

}
