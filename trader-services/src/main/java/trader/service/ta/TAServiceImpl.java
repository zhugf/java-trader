package trader.service.ta;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import trader.common.beans.BeansContainer;
import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableData;
import trader.common.exchangeable.ExchangeableTradingTimes;
import trader.common.exchangeable.MarketTimeStage;
import trader.common.util.TraderHomeUtil;
import trader.service.md.MarketData;
import trader.service.md.MarketDataListener;
import trader.service.md.MarketDataService;
import trader.service.trade.MarketTimeService;

/**
 * 技术分析/KBar实现类.
 * <BR>单线程调用, 不支持多线程
 */
@Service
public class TAServiceImpl implements TAService, MarketDataListener {
    private final static Logger logger = LoggerFactory.getLogger(TAServiceImpl.class);

    private MarketDataService mdService;

    private MarketTimeService mtService;

    private ExchangeableData data;

    private Map<Exchangeable, TAEntry> entries = new HashMap<>();

    private List<TAListener> listeners = new ArrayList<>();

    @Override
    public void init(BeansContainer beansContainer) {
        data = TraderHomeUtil.getExchangeableData();

        mdService = beansContainer.getBean(MarketDataService.class);
        mtService = beansContainer.getBean(MarketTimeService.class);

        long t0=System.currentTimeMillis();
        mdService.addListener(this);
        TreeMap<Exchangeable, List<LocalDate>> historicalDates = new TreeMap<>();
        for(Exchangeable e:mdService.getSubscriptions()) {
            ExchangeableTradingTimes tradingTimes = e.exchange().getTradingTimes(e, mtService.getMarketTime().toLocalDate());
            if ( tradingTimes==null ) {
                continue;
            }
            TAEntry entry = new TAEntry(e);
            entries.put(e, entry);
            try{
                entry.init(beansContainer);
            }catch(Throwable t) {
                logger.error(entry.getExchangeable()+" load historical data failed", t);
            }
            historicalDates.put(e, entry.getHistoricalDates());
        }
        long t1=System.currentTimeMillis();
        logger.info("Start TASevice with data dir "+data.getDataDir()+" in "+(t1-t0)+" ms, "+historicalDates.size()+" exchangeables loaded: "+historicalDates);
    }

    @Override
    @PreDestroy
    public void destroy() {
    }

    @Override
    public TAItem getItem(Exchangeable e) {
        return entries.get(e);
    }

    @Override
    public void addListener(TAListener listener) {
        if ( !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    @Override
    public void onMarketData(MarketData marketData, MarketTimeStage mtStage) {
        TAEntry entry = entries.get(marketData.instrumentId);
        if ( entry!=null ) {
            if ( entry.onMarketData(marketData, mtStage) ) {
                entry.notifyListeners(listeners);
            }
        }
    }

}
