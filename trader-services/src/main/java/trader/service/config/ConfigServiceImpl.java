package trader.service.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import trader.common.config.AbstractConfigService;

@Service
public class ConfigServiceImpl extends AbstractConfigService {

    @Autowired
    private ScheduledExecutorService scheduledExecutorService;

    @Autowired
    private ExecutorService executorService;

    @PostConstruct
    public void init() {
        scheduledExecutorService.scheduleAtFixedRate(()->{
            reloadAll();
        }, CHECK_INTERVAL, CHECK_INTERVAL, TimeUnit.MILLISECONDS);
    }

}
