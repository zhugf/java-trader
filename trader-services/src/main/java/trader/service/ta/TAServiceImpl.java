package trader.service.ta;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import trader.common.tick.PriceLevel;
import trader.common.util.ConversionUtil;
import trader.common.util.StringUtil;
import trader.common.util.TraderHomeUtil;
import trader.service.md.MarketData;
import trader.service.md.MarketDataListener;
import trader.service.md.MarketDataService;

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

    private BeansContainer beansContainer;

    private MarketDataService mdService;

    private ExchangeableData data;

    private ServiceState state = ServiceState.Unknown;

    private Collection<String> subscriptions = new TreeSet<>();

    private Map<Exchangeable, TAEntry> entries = new HashMap<>();

    @Override
    public void init(BeansContainer beansContainer) {
        state = ServiceState.Starting;
        this.beansContainer = beansContainer;
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
            mdService.addListener(this);
            logger.info("Start TASevice with data dir "+data.getDataDir());
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
    public void registerListener(List<Exchangeable> exchangeables, List<PriceLevel> levels, TAListener listener) {
        for(Exchangeable e:exchangeables) {
            TAEntry entry = entries.get(e);
            if ( entry==null) {
                entry = new TAEntry(beansContainer, e);
                entries.put(e, entry);
                mdService.addListener(this, e);
            }
            entry.registerListener(levels, listener);
        }
    }

    @Override
    public void onMarketData(MarketData tick) {
        if ( state==ServiceState.Ready ) {
            TAEntry entry = entries.get(tick.instrument);
            if ( entry!=null ) {
                entry.onMarketData(tick);
            }
        }
    }

}
