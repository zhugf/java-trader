package trader.service.shutdown;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Service;

import trader.common.config.ConfigUtil;
import trader.common.util.DateUtil;
import trader.common.util.StringUtil;

/**
 * 定时主动退出程序
 */
@Service
public class ShutdownTriggerService implements ApplicationListener<ApplicationReadyEvent> {
    private final static Logger logger = LoggerFactory.getLogger(ShutdownTriggerService.class);

    public static final String ITEM_SHUTDOWN_TIMES = "/ShutdownTriggerService/time";

    @Autowired
    private ApplicationContext context;

    @Autowired
    private ScheduledExecutorService scheduledExecutorService;

    private List<LocalTime> times = new ArrayList<>();

    @PostConstruct
    public void init() {
        String timeStr = ConfigUtil.getString(ITEM_SHUTDOWN_TIMES);
        if ( !StringUtil.isEmpty(timeStr) ) {
            for(String time:StringUtil.split(timeStr, ",|;")) {
                times.add(DateUtil.str2localtime(time));
            }
            logger.info("Shutdown times: "+times);
        }else {
            logger.info("No shutdown times found");
        }
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if ( !times.isEmpty() ) {
            scheduledExecutorService.scheduleAtFixedRate(()->{
                checkShutdownTime();
            }, 20, 20, TimeUnit.SECONDS);
        }
    }

    private void checkShutdownTime() {
        LocalTime now = LocalDateTime.now().toLocalTime();
        boolean shutdown = false;
        for(LocalTime time:times) {
            if( now.getHour()==time.getHour() && now.getMinute()==time.getMinute() ) {
                shutdown = true;
                break;
            }
        }
        if ( shutdown ) {
            logger.info("Trader shutdown ...");
            ((ConfigurableApplicationContext) context).close();
        }
    }

}
