package trader.service.ta;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import trader.common.beans.BeansContainer;
import trader.common.beans.ServiceState;
import trader.common.config.ConfigUtil;
import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableData;
import trader.common.exchangeable.ExchangeableTradingTimes;
import trader.common.util.ConversionUtil;
import trader.common.util.StringUtil;
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
    /**
     * 服务状态
     */
    public static final String ITEM_STATE = "/TAService/state";
    /**
     * 主动关注的品种
     */
    public static final String ITEM_SUBSCRIPTIONS = "/TAService/subscriptions";

    private MarketDataService mdService;

    private MarketTimeService mtService;

    private ExchangeableData data;

    private ServiceState state = ServiceState.Unknown;

    private Collection<String> subscriptions = new TreeSet<>();

    private Map<Exchangeable, TAEntry> entries = new HashMap<>();

    private List<TAListener> listeners = new ArrayList<>();

    @Override
    public void init(BeansContainer beansContainer) {
        state = ServiceState.Starting;
        String configState = ConfigUtil.getString(ITEM_STATE);
        if ( !StringUtil.isEmpty(configState)) {
            state = ConversionUtil.toEnum(ServiceState.class, configState);
        }
        String subscriptions = ConfigUtil.getString(ITEM_SUBSCRIPTIONS);
        if ( !StringUtil.isEmpty(subscriptions)) {
            this.subscriptions.addAll( Arrays.asList(StringUtil.split(subscriptions, ",|;|\\s")) );
        }
        if ( state!=ServiceState.Stopped ) {
            data = TraderHomeUtil.getExchangeableData();

            mdService = beansContainer.getBean(MarketDataService.class);
            mtService = beansContainer.getBean(MarketTimeService.class);

            long t0=System.currentTimeMillis();
            mdService.addListener(this);
            TreeMap<Exchangeable, List<LocalDate>> historicalDates = new TreeMap<>();
            for(Exchangeable e: filterMarketDataSubscriptions(mdService.getSubscriptions()) ) {
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
            state = ServiceState.Ready;
        } else {
            logger.info("TAService is stopped");
        }
    }

    @Override
    @PreDestroy
    public void destroy() {
    }

    public void addSubscriptions(String ...subscriptions) {
        this.subscriptions.addAll(Arrays.asList(subscriptions));
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
    public void onMarketData(MarketData marketData) {
        if ( state==ServiceState.Ready ) {
            TAEntry entry = entries.get(marketData.instrumentId);
            if ( entry!=null ) {
                if ( entry.onMarketData(marketData) ) {
                    entry.notifyListeners(listeners);
                }
            }
        }
    }

    /**
     * 根据配置过滤所有合约, 决定哪些合约需要计算技术指标
     */
    private List<Exchangeable> filterMarketDataSubscriptions(Collection<Exchangeable> exchangeables){
        List<Exchangeable> result = new ArrayList<>();
        for(Exchangeable e:exchangeables) {
            boolean matched = false;
            for(String s:subscriptions) {
                if ( e.commodity().equals(s) || e.id().startsWith(s)) {
                    matched = true;
                    break;
                }
            }
            if ( matched ) {
                result.add(e);
            }
        }
        return result;
    }

}
