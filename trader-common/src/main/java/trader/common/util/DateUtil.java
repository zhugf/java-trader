package trader.common.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DateUtil {
    private static final Logger logger = LoggerFactory.getLogger(DateUtil.class);

    public static final String PROP_PUBLIC_HOLIDAYS_DIR = "trader.common.util.publicHolidaysDir";

    private static ZoneId defaultZoneId = ZoneId.systemDefault();

    public static ZoneId UTC = ZoneId.of("UTC");

    /**
     * Asia/Shanghai
     */
    public static ZoneId CTT = ZoneId.of("Asia/Shanghai");

    private static final TimeZone systemTimeZone = detectSystemTimeZone();

    private static TimeZone detectSystemTimeZone() {
        try {
            // return "+0800"
            List<String> osOffsets = SystemUtil.execute("date +%z");
            if (osOffsets.size() > 0) {
                String offset = osOffsets.get(0);
                return TimeZone.getTimeZone("GMT" + offset);
            }
        } catch (Exception e) {
        }
        return TimeZone.getDefault();
    }

    private static TimeSource timeSource = () -> {
        return LocalDateTime.now();
    };

    public static void setTimeSource(TimeSource t) {
        timeSource = t;
    }

    public static LocalDateTime getCurrentTime() {
        return timeSource.getTime();
    }

    public static String getCurrentTimeAsString() {
        return date2str(getCurrentTime());
    }

    public static TimeZone getSystemTimeZone() {
        return systemTimeZone;
    }

    public static String instant2str(Instant instant) {
        if (instant == null) {
            return "";
        }
        return instant.toString();
    }

    public static Instant str2instant(String str) {
        if (str == null || str.equalsIgnoreCase("null") || str.length() == 0) {
            return null;
        }
        try {
            return Instant.parse(str);
        } catch (Exception e) {
            return null;
        }
    }

    public static void setDefaultZoneId(ZoneId zoneId) {
        defaultZoneId = zoneId;
    }

    public static ZoneId getDefaultZoneId() {
        return defaultZoneId;
    }

    /**
     * Convert long time to local date time with default zoneId
     */
    public static LocalDateTime long2datetime(long time) {
        return Instant.ofEpochMilli(time).atZone(defaultZoneId).toLocalDateTime();
    }

    public static LocalDateTime long2datetime(ZoneId zoneId, long time) {
        return Instant.ofEpochMilli(time).atZone(zoneId).toLocalDateTime();
    }

    public static long localdatetime2seconds(LocalDateTime ldt) {
        return ZonedDateTime.of(ldt, defaultZoneId).toEpochSecond();
    }

    public static long localdatetime2long(LocalDateTime ldt) {
        return instant2long(ZonedDateTime.of(ldt, defaultZoneId).toInstant());
    }

    public static long localdatetime2long(ZoneId zoneId, LocalDateTime ldt) {
        if (ldt == null) {
            return 0;
        }
        return instant2long(ZonedDateTime.of(ldt, zoneId).toInstant());
    }

    public static long instant2long(Instant instant) {
        if (instant == null) {
            return 0;
        }
        long epochSeconds = instant.getEpochSecond();
        long nanoSeconds = instant.getNano();
        return epochSeconds * 1000 + (nanoSeconds / 1000000);
    }

    public static Duration between(LocalDateTime datetime1, LocalDateTime datetime2) {
        if (datetime1.isAfter(datetime2)) {
            return between(datetime2, datetime1);
        }
        ZonedDateTime zonedTime1 = datetime1.atZone(defaultZoneId);
        ZonedDateTime zonedTime2 = datetime2.atZone(defaultZoneId);
        long seconds = zonedTime2.toEpochSecond() - zonedTime1.toEpochSecond();
        long nanoAdjustment = zonedTime2.get(ChronoField.NANO_OF_SECOND) - zonedTime1.get(ChronoField.NANO_OF_SECOND);
        return Duration.ofSeconds(seconds, nanoAdjustment);
    }

    private static final DateTimeFormatter date2strFormater = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ENGLISH);

    private static final DateTimeFormatter datetime2strFormater = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH);

    private static final DateTimeFormatter[] dateFormaters = new DateTimeFormatter[] { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[.SSS]", Locale.ENGLISH),
            DateTimeFormatter.ISO_DATE_TIME, DateTimeFormatter.ISO_LOCAL_DATE_TIME, DateTimeFormatter.ISO_INSTANT,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH), DateTimeFormatter.ofPattern("yyyy-MMM-dd HH:mm:ss X", Locale.ENGLISH) };

    private static final DateTimeFormatter[] timeFormaters = new DateTimeFormatter[] { DateTimeFormatter.ofPattern("HH:mm:ss.SSS", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ENGLISH), DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH) };

    public static LocalDateTime str2localdatetime(String str) {
        if (StringUtil.isEmpty(str)) {
            return null;
        }
        for (int i = 0; i < dateFormaters.length; i++) {
            try {
                return LocalDateTime.parse(str, dateFormaters[i]);
            } catch (Exception e) {
            }
        }
        return null;
    }

    /**
     *
     * @param dateInyyyymmdd
     * @param timeHHCMMCSS
     *            time in format "10:15:30"
     * @param millisec
     * @return
     */
    public static LocalDateTime str2localdatetime(String dateInyyyymmdd, String timeHHCMMCSS, int millisec) {
        LocalDate localDate = str2localdate(dateInyyyymmdd);
        return str2localdatetime(localDate, timeHHCMMCSS, millisec);
    }

    public static LocalDateTime str2localdatetime(LocalDate localDate, String timeHHCMMCSS, int millisec) {
        if (timeHHCMMCSS.length() == 0) {
            return localDate.atTime(0, 0, 0);
        }
        if (timeHHCMMCSS.length() == 7) {
            timeHHCMMCSS = "0" + timeHHCMMCSS;
        }
        LocalTime localTime = LocalTime.parse(timeHHCMMCSS);
        return localDate.atTime(localTime.getHour(), localTime.getMinute(), localTime.getSecond(), millisec * 1000000);
    }

    /**
     * 转换09:00:00格式为: 90000, 转换12:00:00格式为12,00,00
     */
    public static int time2int(String timeHHCMMCSS) {
        if (timeHHCMMCSS.length() == 7) {
            timeHHCMMCSS = "0" + timeHHCMMCSS;
        }
        int hour = Integer.parseInt(timeHHCMMCSS.substring(0, 2));
        int min = Integer.parseInt(timeHHCMMCSS.substring(3, 5));
        int sec = Integer.parseInt(timeHHCMMCSS.substring(6, 8));

        return hour*10000+min*100+sec;
    }

    public static LocalDate str2localdate(String str) {
        if (StringUtil.isEmpty(str)) {
            return null;
        }
        try {
            return LocalDate.parse(str, date2strFormater);
        } catch (Throwable t) {
        }
        try {
            return LocalDate.parse(str);
        } catch (Throwable t) {
        }
        return null;
    }

    public static String date2str(LocalDate date) {
        if (date == null) {
            return null;
        }
        return date2strFormater.format(date);
    }

    public static String date2str(LocalDateTime date) {
        if (date == null) {
            return null;
        }
        return datetime2strFormater.format(date);
    }

    public static long date2epochMillis(ZonedDateTime zdt) {
        return zdt.toEpochSecond()*1000 + zdt.getNano()/1000000;
    }

    public static LocalTime str2localtime(String str) {
        if (StringUtil.isEmpty(str)) {
            return null;
        }
        for (int i = 0; i < timeFormaters.length; i++) {
            try {
                return LocalTime.parse(str, timeFormaters[i]);
            } catch (Exception e) {
            }
        }
        return null;
    }

    /**
     * 规整时间的秒
     */
    public static LocalDateTime round(LocalDateTime ldt) {
        int seconds = ldt.get(ChronoField.SECOND_OF_MINUTE);
        if (seconds >= 59) {
            ldt = ldt.plus(1, ChronoUnit.SECONDS);
        }
        return ldt.truncatedTo(ChronoUnit.SECONDS);
    }

    /**
     * 缓存的节假日定义
     */
    private static final Map<Locale, List<String>> cachedHolidays = new HashMap<>();

    private static synchronized List<String> getHolidaysText(Locale locale) {
        if (locale == null) {
            locale = Locale.getDefault();
        }
        List<String> publicHolidays = cachedHolidays.get(locale);
        if (publicHolidays != null) {
            return publicHolidays;
        }

        // 加载假日文件
        String publicHolidaysText = null;
        String propHolidaysDir = System.getProperty(PROP_PUBLIC_HOLIDAYS_DIR);
        if (!StringUtil.isEmpty(propHolidaysDir)) {
            File holidaysFile = ResourceUtil.loadLocalizedFile(new File(propHolidaysDir), "publicHolidays.txt", locale);
            if (holidaysFile != null && holidaysFile.exists()) {
                try {
                    publicHolidaysText = FileUtil.read(holidaysFile);
                } catch (IOException e) {
                    logger.error("Unable to load public holidays file from " + propHolidaysDir + " with local " + locale);
                }
            }
        }
        if (publicHolidaysText == null) {
            publicHolidaysText = ResourceUtil.loadLocalizedResource(DateUtil.class.getClassLoader(), "trader.common.util", "publicHolidays.txt", locale);
        }
        // 解析假日文件
        publicHolidays = new ArrayList<>(100);
        try (BufferedReader reader = new BufferedReader(new StringReader(publicHolidaysText));) {
            String line = null;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.length() == 0 || line.startsWith("#")) {
                    continue;
                }
                publicHolidays.add(line);
            }
        } catch (IOException ioe) {
        }

        cachedHolidays.put(locale, publicHolidays);
        return publicHolidays;
    }

    /**
     * 检查某日是否工作日
     */
    public static boolean isWorkingDay(LocalDate date, Locale locale) {
        List<String> publicHolidays = getHolidaysText(locale);

        boolean isHoliday = false;
        boolean isWorkingDay = false;
        String datestr = date2str(date);
        String datestr2 = "!" + datestr;
        for (String line : publicHolidays) {
            if (datestr.equals(line)) {
                isHoliday = true;
                break;
            }
            if (datestr2.equals(line)) {
                isWorkingDay = true;
                break;
            }
        }
        if (isHoliday) {
            return false;
        }
        if (isWorkingDay) {
            return true;
        }
        // 没找到,检查是否周1-周5
        switch (date.getDayOfWeek()) {
        case SATURDAY:
        case SUNDAY:
            return false;
        default:
            return true;
        }
    }

    public static String duration2str(Duration duration) {
        long seconds = duration.getSeconds();
        long hours = seconds / (60 * 60);
        int minutes = (int) ((seconds % (60 * 60)) / (60));
        int secs = (int) (seconds % (60));

        StringBuilder buf = new StringBuilder(64);
        if (hours > 0) {
            buf.append(hours).append(hours > 1 ? " hours" : " hour");
        }
        if (minutes > 0) {
            buf.append(" ").append(minutes).append(minutes > 1 ? " mins" : " min");
        }
        if (secs > 0) {
            buf.append(" ").append(secs).append(secs > 1 ? " secs" : " sec");
        }
        return buf.toString().trim();
    }

}
