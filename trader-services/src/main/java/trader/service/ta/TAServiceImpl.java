package trader.service.ta;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.ta4j.core.TimeSeries;

import trader.common.beans.BeansContainer;
import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableData;
import trader.common.tick.PriceLevel;
import trader.common.util.TraderHomeUtil;
import trader.service.md.MarketData;
import trader.service.md.MarketDataListener;
import trader.service.md.MarketDataService;
import trader.service.trade.MarketTimeService;

/**
 * 技术分析/KBar实现类.
 */
@Service
public class TAServiceImpl implements TAService, MarketDataListener {
    private final static Logger logger = LoggerFactory.getLogger(TAServiceImpl.class);

    private MarketDataService mdService;

    private MarketTimeService mtService;

    private ExchangeableData data;

    private Map<Exchangeable, TAEntry> entries = new HashMap<>();

    @Override
    public void init(BeansContainer beansContainer) {
        data = TraderHomeUtil.getExchangeableData();

        mdService = beansContainer.getBean(MarketDataService.class);
        mtService = beansContainer.getBean(MarketTimeService.class);

        long t0=System.currentTimeMillis();
        mdService.addListener(this);
        for(Exchangeable e:mdService.getSubscriptions()) {
            LocalDate tradingDay = e.detectTradingDay(mtService.getMarketTime());
            if ( tradingDay==null ) {
                continue;
            }
            TAEntry entry = new TAEntry(e);
            entries.put(e, entry);
            try{
                entry.init(beansContainer);
            }catch(Throwable t) {
                logger.error("加载 "+entry.getExchangeable()+" 历史数据失败", t);
            }
        }
        long t1=System.currentTimeMillis();
        logger.info("Start TASevice with data dir "+data.getDataDir()+" in "+(t1-t0)+" ms, exchangeables loaded: "+(new TreeSet<>(entries.keySet())));
    }

    @Override
    @PreDestroy
    public void destroy() {
//        for(TAEntry entry:entries.values()) {
//            entry.dumpStats();
//        }
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
