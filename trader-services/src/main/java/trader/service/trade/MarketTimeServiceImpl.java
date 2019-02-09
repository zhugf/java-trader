package trader.service.trade;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.ScheduledExecutorService;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 交易时间服务
 */
@Service
public class MarketTimeServiceImpl implements MarketTimeService {
    private final static Logger logger = LoggerFactory.getLogger(MarketTimeServiceImpl.class);

    @Autowired
    private ScheduledExecutorService scheduledExecutorService;

    @PostConstruct
    public void init() {
    }

    @Override
    public long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    /**
     * 返回市场时间
     */
    @Override
    public LocalDateTime getMarketTime() {
        return LocalDateTime.now();
    }

    /**
     * 返回市场当天时间
     */
    @Override
    public LocalDate getMarketDay() {
        return LocalDate.now();
    }

}
