package trader.common.util;

import java.text.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class FormatUtil {

    static final DateTimeFormatter[] DATETIME_FORMATTERS = new DateTimeFormatter[] {
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            ,DateTimeFormatter.ofPattern("yyyyMMddHHmm")
    };
    static final DateTimeFormatter[] DATE_FORMATTERS = new DateTimeFormatter[] {
            DateTimeFormatter.ofPattern("yyyyMMdd")
            ,DateTimeFormatter.ofPattern("yyyy-MM-dd")
    };

    public static LocalDateTime formatDateTime(String str) {
        for(int i=0;i<DATETIME_FORMATTERS.length;i++) {
            try{
                return LocalDateTime.parse(str, DATETIME_FORMATTERS[i]);
            }catch(Throwable t) {}
        }
        return null;
    }

    public static LocalDate formatDate(String str) {
        for(int i=0;i<DATE_FORMATTERS.length;i++) {
            try{
                return LocalDate.parse(str, DATE_FORMATTERS[i]);
            }catch(Throwable t) {}
        }
        return null;
    }

    /**
     * 为每个线程创建一个实例
     */
    static class DateUtilFormats{
        final DateTimeFormatter dateFormatTimestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
        final DateTimeFormatter hhmmss2 = DateTimeFormatter.ofPattern("HH:mm:ss");

        final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        final SimpleDateFormat yyyymmdd = new SimpleDateFormat("yyyyMMdd");
        final SimpleDateFormat hhmmss = new SimpleDateFormat("HH:mm:ss");
        final MessageFormat hmsFormat = new MessageFormat("{0,number,00}:{1,number,00}:{2,number,00}");
        final Map<String,Format> formatMap = new HashMap<>();
    }

    private static final ThreadLocal<DateUtilFormats> localFormats = ThreadLocal.<DateUtilFormats>withInitial(()->{return new DateUtilFormats();});
    static DateUtilFormats getLocalFormats(){
        return localFormats.get();
    }

    public static Format getDecimalFormat(String formatPattern){
        Map<String,Format> formatMap = localFormats.get().formatMap;
        Format result = formatMap.get(formatPattern);
        if ( result==null ){
            result = new DecimalFormat(formatPattern);
            formatMap.put(formatPattern, result);
        }
        return result;
    }

}
