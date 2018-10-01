package trader.service.lifecycle;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import trader.service.md.MarketDataService;
import trader.service.trade.TradeService;

/**
 * 启动过程启动
 */
@Service
public class StartupService {
    private final static Logger logger = LoggerFactory.getLogger(StartupService.class);

    @Autowired
    private MarketDataService marketDataService;

    @Autowired
    private TradeService tradeService;

    @PostConstruct
    public void init() {

    }

    /**
     * 初始化
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady(){
    }

}
