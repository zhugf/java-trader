package trader.common.util;

import java.math.BigInteger;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConversionUtil {
    private static final Logger logger = LoggerFactory.getLogger(ConversionUtil.class);

    private static Class strings = (new String[] {}).getClass();

    public static boolean isStringArray(Object obj) {
        return strings==obj.getClass();
    }

    public static Object toType(Class targetType, Object o) {
        if ( targetType==int.class || targetType==Integer.class ) {
            return toInt(o, true);
        }else if (targetType==long.class || targetType==Long.class || targetType==BigInteger.class) {
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

    static boolean isNull(Object obj) {
        return null==obj || com.google.gson.JsonNull.INSTANCE.equals(obj);
    }

    public static boolean toBoolean(Object obj) {
        return toBoolean(obj, false);
    }

    public static boolean toBoolean(Object obj, boolean defaultValue) {
        if ( isNull(obj)) {
            return defaultValue;
        }
        if ( obj instanceof Boolean) {
            return ((Boolean)obj).booleanValue();
        }
        else if ( obj.getClass() == boolean.class ) {
            return ((Boolean)obj).booleanValue();
        }
        String str = obj.toString().trim();
        if ( StringUtil.equalsIgnoreCase("true", str)
                || StringUtil.equalsIgnoreCase("yes", str)
                || StringUtil.equalsIgnoreCase("1", str)
                ) {
            return true;
        }
        if (StringUtil.equalsIgnoreCase("false", str)
                || StringUtil.equalsIgnoreCase("no", str)
                || StringUtil.equalsIgnoreCase("0", str)
                ) {
            return false;
        }
        return defaultValue;
    }

    public static int toInt(Object obj){
        return toInt(obj, false, 0);
    }

    public static int toInt(Object obj, int value) {
        return toInt(obj, true, value);
    }

    public static int toInt(Object obj, boolean catchException) {
        return toInt(obj, catchException, 0);
    }

    public static Integer toInteger(Object obj) {
        return toInt(obj, false, 0);
    }

    public static int toInt(Object obj, boolean catchException, int defaultValue) {
        if ( isNull(obj)) {
            return defaultValue;
        }
        if ( obj instanceof Number){
            return ((Number)obj).intValue();
        }
        String str = obj.toString();
        if ( StringUtil.isEmpty(str)) {
            return defaultValue;
        }
        String temp = str.trim();
        if(StringUtil.isEmpty(temp) || temp.equals("-") || temp.equals("+")) {
            return defaultValue;
        }
        try{
            if (str.indexOf(".") > -1) {
                return (int) Double.parseDouble(str);
            }
            return Integer.parseInt(StringUtil.unquotes(temp));
        }catch(RuntimeException e) {
            if ( catchException ) {
                return defaultValue;
            }
            throw new RuntimeException("Convert "+obj+" to int failed: "+e.toString(), e);
        }
    }

    public static long toLong(Object obj) {
        return toLong(obj, false, 0);
    }

    public static long toLong(Object obj, boolean catchException) {
        return toLong(obj, catchException, 0);
    }

    public static long toLong(String str, long defaultValue)
    {
        return toLong(str, true, defaultValue);
    }

    public static long toLong(Object obj, boolean catchException, long defaultValue){
        if ( isNull(obj)) {
            return defaultValue;
        }
        if ( obj instanceof Number){
            return ((Number)obj).longValue();
        }
        String str = obj.toString();
        if ( StringUtil.isEmpty(str)) {
            return defaultValue;
        }
        String temp = str.trim();
        if(StringUtil.isEmpty(temp) || temp.equals("-") || temp.equals("+")) {
            return defaultValue;
        }
        try{
            temp = StringUtil.unquotes(temp).toLowerCase();
            int radix=10;
            for(int i=0;i<temp.length();i++) {
                char c = temp.charAt(i);
                if ( c>'9' ) {
                    radix=16;
                    break;
                }
            }
            if ( temp.startsWith("0")) {
                radix=16;
            }
            return Long.parseLong(temp, radix);
        }catch(RuntimeException e) {
            if ( catchException ) {
                return defaultValue;
            }
            throw new RuntimeException("Convert "+obj+" to long failed: "+e.toString(), e);
        }
    }

    // 保留小数点后两位
    public static double toDoubleFormated(Object obj) {
        DecimalFormat df = new DecimalFormat("#.00");
        return toDouble(df.format(toDouble(obj)));
    }

    public static double toDouble(Object obj) {
        return toDouble(obj, false);
    }

    public static double toDouble(Object obj, boolean catchException) {
        if ( isNull(obj)) {
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
            throw new RuntimeException("Convert "+obj+" to double failed: "+re.toString(),re);
        }
    }

    public static double toDouble(Object obj, double v) {
        double result = v;
        if ( !StringUtil.isEmpty(obj)) {
            try{
                result = Double.parseDouble(ConversionUtil.toString(obj));
            }catch(Throwable t) {}
        }
        return result;
    }

    public static Number toNumber(Object obj, boolean catchException) {
        if ( obj instanceof Number ) {
            return (Number)obj;
        }
        String str = obj.toString();
        if ( str.indexOf(".")>=0 ) {
            return toDouble(str, catchException);
        } else {
            return toLong(str, catchException);
        }
    }

    public static List toList(Object value) {
        if ( isNull(value)) {
            return Collections.emptyList();
        }
        if ( value instanceof List ) {
            return (List)value;
        }
        List result = new ArrayList();
        if ( value instanceof Iterable ) {
            Iterable i=(Iterable)value;
            for(Iterator it=i.iterator();it.hasNext();) {
                result.add(it.next());
            }
        } else if ( value.getClass().isArray() ){
            Class clazz = value.getClass().getComponentType();
            if (clazz == int.class) {
                int[] values = (int[])value;
                for(int i=0;i<values.length;i++) {
                    result.add(values[i]);
                }
            } else if (clazz==long.class) {
                long[] values = (long[])value;
                for(int i=0;i<values.length;i++) {
                    result.add(values[i]);
                }
            } else if (clazz==boolean.class) {
                boolean[] values = (boolean[])value;
                for(int i=0;i<values.length;i++) {
                    result.add(values[i]);
                }
            } else if (clazz== double.class) {
                double[] values = (double[])value;
                for(int i=0;i<values.length;i++) {
                    result.add(values[i]);
                }
            } else if (clazz== byte.class) {
                byte[] values = (byte[])value;
                for(int i=0;i<values.length;i++) {
                    result.add(values[i]);
                }
            } else if (clazz== short.class) {
                short[] values = (short[])value;
                for(int i=0;i<values.length;i++) {
                    result.add(values[i]);
                }
            } else {
                result.addAll(Arrays.asList((Object[])value));
            }
        }else {
            result.add(value);
        }
        return result;
    }

    public static LocalDateTime obj2datetime(Object value) {
        if ( isNull(value)) {
            return null;
        }
        if ( value.getClass()==LocalDateTime.class ) {
            return (LocalDateTime)value;
        } else if ( value.getClass()==Instant.class ) {
            return DateUtil.long2datetime(DateUtil.instant2long((Instant)value));
        } else {
            long timestamp = ConversionUtil.toLong(value, true);
            if ( timestamp>0 ) {
                return DateUtil.long2datetime(timestamp);
            } else {
                return DateUtil.str2localdatetime(ConversionUtil.toString(value));
            }
        }
    }

    public static long str2size(String str) {
        if (StringUtil.isEmpty(str)) {
            return 0;
        }
        str = str.toLowerCase();
        int begin = 0;
        int cursor = 1;
        long size = 0;
        while (cursor < str.length()) {
            if (str.charAt(cursor) >= 'a' && str.charAt(cursor) <= 'z') {
                cursor++;
                String sstr = str.substring(begin, cursor);
                size += str2size0(sstr);
                begin = cursor;
            }
            cursor++;
        }
        if (begin < str.length()) {
            size += str2size0(str.substring(begin));
        }
        return size;
    }

    /**
     * Convert 1m, 1k, 1
     */
    private static long str2size0(String str) {
        if (StringUtil.isEmpty(str)) {
            return 0;
        }
        str = str.toLowerCase();
        long unit = 1;
        if ( str.endsWith("t")) {
            unit = 1024L*1024L*1024L*1024L;
            str = str.substring(0, str.length()-1);
        } else if ( str.endsWith("g")) {
            unit = 1024*1024*1024;
            str = str.substring(0, str.length()-1);
        } else if ( str.endsWith("m")) {
            unit = 1024*1024;
            str = str.substring(0, str.length()-1);
        } else if ( str.endsWith("k")) {
            unit = 1024;
            str = str.substring(0, str.length()-1);
        }
        return ((long)(Double.parseDouble(str))) * unit;
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
        }else if ( str.endsWith("w")) {
            unit = 60*60*24*7;
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
        if ( isNull(field)) {
            return 0;
        }
        if ( field instanceof Number) { //Epoch Millies
            return ((Number)field).longValue();
        }
        return DateUtil.instant2long(toInstant(field));
    }

    public static Instant toInstant(Object field) {
        if ( isNull(field)) {
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
        if ( isNull(field)) {
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
        if ( isNull(obj)) {
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
        StringBuilder str2 = new StringBuilder(str.length());
        for(int i=0;i<str.length();i++) {
            char ch = str.charAt(i);
            switch(ch) {
            case '-':
                break;
            default:
                str2.append(ch);
                break;
            }
        }
        str = str2.toString(); //str.replaceAll("-", "");
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
        if ( isNull(value)) {
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

    public static String[] list2strings(List list) {
        if ( isNull(list) ) {
            return null;
        }
        String[] result = new String[list.size()];
        for(int i=0;i<list.size();i++) {
            result[i] = toString(list.get(i));
        }
        return result;
    }

    public static String list2String(List<String> data) {
        if ( isNull(data)) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (String s : data) {
            builder.append(s).append("||");
        }
        String result = builder.toString();
        if (result.endsWith("||")) {
            result = result.substring(0, result.length() - 2);
        }
        return result;
    }

}
