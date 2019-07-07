package trader.service.log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LogServiceImpl implements LogService {
    private static final Logger logger = LoggerFactory.getLogger(LogServiceImpl.class);

    private final static String LOGBACK = "logback";
    private final static String LOG4J = "log4j";

    private static String logProvider;

    private static Class logbackLoggerClass;
    private static Class logbackLevelClass;
    static{
        try {
            logbackLoggerClass = Class.forName("ch.qos.logback.classic.Logger");
            logbackLevelClass = Class.forName("ch.qos.logback.classic.Level");
        }catch(Throwable t) {}
        if ( logger.getClass()==logbackLoggerClass ) {
            logProvider = LOGBACK;
        }else if ( logger.getClass().getName().toLowerCase().indexOf("log4j")>0 ) {
            logProvider = LOG4J;
        }
    }

    public LogServiceImpl() {

    }

    private LogListener listener;

    @Override
    public void setListener(LogListener listener) {
        this.listener = listener;
    }

    @Override
    public LogLevelInfo getLevel(String category) {
        return getLogLevel(category);
    }

    @Override
    public void setLevel(String category, String levelStr, boolean propagate) {
        if ( logger.isInfoEnabled() ) {
            logger.info("set category "+category+" level "+levelStr+(propagate?" propagation":""));
        }
        setLogLevel(category, levelStr);

        if ( propagate && listener!=null ) {
            listener.onSetLevel(category, levelStr);
        }
    }

    public static LogLevelInfo getLogLevel(String category) {
        LogLevelInfo result = new LogLevelInfo();
        Logger categoryLogger = LoggerFactory.getLogger(category);

        boolean foundFromReflection=false;
        switch(logProvider) {
        case LOGBACK:
            try {
                Method methodGetLevel = categoryLogger.getClass().getDeclaredMethod("getLevel");
                Field fieldParent = categoryLogger.getClass().getDeclaredField("parent");
                fieldParent.setAccessible(true);
                while(true) {
                    Object level = methodGetLevel.invoke(categoryLogger);
                    if ( level!=null ) {
                        result.setLevel(level.toString());
                        foundFromReflection = true;
                        break;
                    }
                    categoryLogger = (Logger)fieldParent.get(categoryLogger);
                    result.setInherited(true);
                }
            } catch (Throwable  t) {}
        case LOG4J:
            break;
        }
        if ( !foundFromReflection) {
            if ( categoryLogger.isTraceEnabled()) {
                result.setLevel("TRACE");
            }else if (categoryLogger.isDebugEnabled() ) {
                result.setLevel("DEBUG");
            }else if (categoryLogger.isInfoEnabled()) {
                result.setLevel("INFO");
            }else if (categoryLogger.isWarnEnabled()) {
                result.setLevel("WARN");
            }else if ( categoryLogger.isErrorEnabled()) {
                result.setLevel("ERROR");
            }
        }
        return result;
    }

    public static void setLogLevel(String category, String levelStr) {
        Logger categoryLogger = LoggerFactory.getLogger(category);
        boolean levelChanged=false;
        switch(logProvider) {
        case LOGBACK:
            try {
                Method toLevelMethod = logbackLevelClass.getMethod("toLevel", String.class);
                Object level = toLevelMethod.invoke(null, levelStr);
                Method method = categoryLogger.getClass().getMethod("setLevel", logbackLevelClass);
                method.invoke(categoryLogger, level);
                levelChanged = true;
            } catch (Throwable  t) {
                logger.error("Set logback level failed", t);
            }
            break;
        case LOG4J:
            try {
                Class log4jLevelClass = Class.forName("org.apache.log4j.Level");
                Field loggerField = categoryLogger.getClass().getDeclaredField("logger");
                loggerField.setAccessible(true);
                Object log4jLogger = loggerField.get(categoryLogger);
                Method log4jLogger_setLevel = log4jLogger.getClass().getMethod("setLevel", log4jLevelClass);
                Method log4jLevel_toLevel = log4jLevelClass.getMethod("toLevel", String.class);
                Object logj4jLevel = log4jLevel_toLevel.invoke(null, levelStr);
                log4jLogger_setLevel.invoke(log4jLogger, logj4jLevel);
                levelChanged = true;
            }catch(Throwable t) {
                logger.error("Set log4j level failed", t);
            }
            break;
        };
        if ( !levelChanged ) {
            logger.error("Set log category "+category+" level "+levelStr+" failed");
        }
        return;
    }

}
