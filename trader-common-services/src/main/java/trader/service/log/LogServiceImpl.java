package trader.service.log;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import trader.common.beans.BeansContainer;
import trader.common.beans.*;
import trader.common.config.ConfigUtil;
import trader.common.util.FileUtil;
import trader.common.util.StringUtil;
import trader.common.util.SystemUtil;
import trader.common.util.TraderHomeUtil;

@Service
public class LogServiceImpl implements LogService {
    private static final Logger logger = LoggerFactory.getLogger(LogServiceImpl.class);

    private final static String ITEM_LOG_LEVEL_FILE = "logLevelFile";

    private final static String LOGBACK = "logback";
    private final static String LOG4J = "log4j";

    private static String logProvider = LOGBACK;

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

    @Autowired
    private BeansContainer beansContainer;

    @Autowired
    private ExecutorService executorService;

    @Autowired
    private ScheduledExecutorService scheduledExecutorService;

    private LogListener listener;

    private Throwable lastException;
    private String lastLogLevels;

    @PostConstruct
    public void init() {
        //启动时立刻设置LogLevel
        applyLogLevelFile();
        //系统启动成功后, 定期扫描配置文件
        ServiceEventHub serviceEventHub = beansContainer.getBean(ServiceEventHub.class);
        serviceEventHub.addListener((ServiceEvent event)->{
            scheduledExecutorService.scheduleAtFixedRate(()->{
                applyLogLevelFile();
            }, 1, 1, TimeUnit.MINUTES);
        }, ServiceEventHub.TOPIC_SERVICE_ALL_INIT_DONE);
    }

    private void applyLogLevelFile() {
        String configPrefix = LogService.class.getSimpleName()+".";
        String logLevelFile = ConfigUtil.getString(configPrefix+ITEM_LOG_LEVEL_FILE);
        if ( !StringUtil.isEmpty(logLevelFile)) {
            // 一行一个级别
            // DEBUG, com.lanysec.service
            try {
                File file = FileUtil.relativePath(new File(System.getProperty(TraderHomeUtil.PROP_TRADER_CONFIG_FILE)), logLevelFile);
                String text = FileUtil.read(file);
                if ( !StringUtil.equalsIgnoreCase(lastLogLevels, text)) {
                    lastLogLevels = text;
                    for(String line:StringUtil.text2lines(text, true, true)) {
                        //忽略#注释
                        if (StringUtil.trim(line).startsWith("#")) {
                            continue;
                        }
                        String parts[] = StringUtil.split(line, ",|;|\\s");
                        if (parts.length>=2 ) {
                            setLevel(parts[1], parts[0], false);
                        }
                    }
                }
                lastException = null;
            }catch(Throwable t) {
                String message = "Apply log levels fail from "+logLevelFile;
                if (t.equals(lastException)) {
                    logger.debug(message, t);
                } else {
                    logger.warn(message, t);
                }
                lastException = t;
            }
        }
    }

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
        String logmsg = "Category "+category+" set log level "+levelStr+(propagate?" propagation":"");
        if ( propagate ) {
            logger.info(logmsg);
        } else {
            logger.debug(logmsg);
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
