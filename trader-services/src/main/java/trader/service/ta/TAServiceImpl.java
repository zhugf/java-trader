package trader.service.ta;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.ta4j.core.TimeSeries;

import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableData;
import trader.common.tick.PriceLevel;
import trader.common.util.TraderHomeUtil;
import trader.service.md.MarketData;
import trader.service.md.MarketDataListener;
import trader.service.md.MarketDataService;

/**
 * 技术分析/KBar实现类
 */
@Service
public class TAServiceImpl implements TAService, MarketDataListener {
    private final static Logger logger = LoggerFactory.getLogger(TAServiceImpl.class);

    @Autowired
    private MarketDataService mdService;

    @Autowired
    private ExecutorService executorService;

    private ExchangeableData data;

    private Map<Exchangeable, TAEntry> entries = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        data = new ExchangeableData(TraderHomeUtil.getDirectory(TraderHomeUtil.DIR_REPOSITORY), false);
        mdService.addListener(this);
        logger.info("Start TASevice with data dir "+data.getDataDir());
    }

    /**
     * 启动后, 连接行情数据源
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady(){
        for(Exchangeable e:mdService.getSubscriptions()) {

        }
    }

    @Override
    public TimeSeries getSeries(Exchangeable e, PriceLevel level) {
        TAEntry entry = entries.get(e);
        if ( entry==null ) {
            return null;
        }
        return entry.getSeries(level);
    }

    @Override
    public void onMarketData(MarketData marketData) {
        TAEntry entry = entries.get(marketData.instrumentId);
        if ( entry!=null ) {
            entry.onMarketData(marketData);
        }
    }

}
