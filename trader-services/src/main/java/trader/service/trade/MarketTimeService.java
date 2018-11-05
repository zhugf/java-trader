package trader.service.trade;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 交易时间服务
 */
@Service
public class MarketTimeService {
    private final static Logger logger = LoggerFactory.getLogger(MarketTimeService.class);

    @Autowired
    private ScheduledExecutorService scheduledExecutorService;

    private volatile LocalDate marketDay;

    @PostConstruct
    public void init() {
        scheduledExecutorService.scheduleAtFixedRate(()->{
            marketDay = LocalDate.now();
        }, 1, 1, TimeUnit.SECONDS);
    }

    /**
     * 返回市场时间
     */
    public LocalDateTime getMarketTime() {
        return LocalDateTime.now();
    }

    /**
     * 返回市场当天时间
     */
    public LocalDate getMarketDay() {
        return marketDay;
    }

}
