package trader.service.ta;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import trader.common.beans.BeansContainer;
import trader.common.beans.ServiceEventHub;
import trader.common.beans.ServiceState;
import trader.common.config.ConfigUtil;
import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableData;
import trader.common.exchangeable.ExchangeableType;
import trader.common.exchangeable.FutureCombo;
import trader.common.util.TraderHomeUtil;
import trader.service.md.MarketData;
import trader.service.md.MarketDataListener;
import trader.service.md.MarketDataService;

/**
 * 技术分析/KBar实现类.
 * <BR>单线程调用, 不支持多线程
 */
@Service
public class BarServiceImpl implements BarService, MarketDataListener {
    private final static Logger logger = LoggerFactory.getLogger(BarServiceImpl.class);
    /**
     * 关注的品种定义
     */
    public static final String ITEM_INSTRUMENTS = "instrument[]";

    @Autowired
    private BeansContainer beansContainer;

    @Autowired
    private MarketDataService mdService;

    private ExchangeableData data;

    private ServiceState state = ServiceState.NotInited;

    private Map<String, InstrumentDef> instrumentDefs = new HashMap<>();

    private Map<Exchangeable, BarAccessImpl> accessors = new HashMap<>();

    public ServiceState getState() {
        return state;
    }

    /**
     * Springas环境初始化
     */
    @PostConstruct
    public void init() {
        ServiceEventHub serviceEventHub = beansContainer.getBean(ServiceEventHub.class);
        if ( null!=serviceEventHub ) {
            serviceEventHub.registerServiceInitializer(getClass().getName(), ()->{
                return init0();
            }, mdService);
        } else {
            init0();
        }
    }

    /**
     * 回测环境的初始化
     */
    public void init(BeansContainer beansContainer) {
        this.beansContainer = beansContainer;
        this.mdService = beansContainer.getBean(MarketDataService.class);
        init0();
    }

    private BarService init0() {
        state = ServiceState.Starting;
        String configPrefix = BarService.class.getSimpleName()+".";
        data = TraderHomeUtil.getExchangeableData();
        mdService = beansContainer.getBean(MarketDataService.class);
        mdService.addListener(this);
        instrumentDefs.putAll( loadInstrumentDefs(configPrefix));
        buildAccessors();
        logger.info("Start with data dir "+data.getDataDir());
        state = ServiceState.Ready;
        return this;
    }

    @PreDestroy
    public void destroy() {
    }

    @Override
    public BarAccess forInstrument(Exchangeable instrument) {
        return accessors.get(instrument);
    }

    @Override
    public Collection<Exchangeable> getInstruments(){
        return new ArrayList<>(accessors.keySet());
    }

    @Override
    public boolean registerListener(List<Exchangeable> instruments, BarListener listener) {
        boolean result = false;
        for(Exchangeable instrument:instruments) {
            BarAccessImpl accessImpl = buildTechAccess(instrument);
            if ( accessImpl!=null ) {
                accessImpl.registerListener(listener);
                result = true;
            }
        }
        return result;
    }

    @Override
    public void onMarketData(MarketData tick) {
        if ( state==ServiceState.Ready ) {
            BarAccessImpl accessor = accessors.get(tick.instrument);
            if ( accessor!=null ) {
                accessor.onMarketData(tick);
            }
        }
    }

    public void addInstrumentDef(InstrumentDef instrumentDef) {
        instrumentDefs.put(instrumentDef.key, instrumentDef);
    }

    private Map<String,InstrumentDef> loadInstrumentDefs(String configPrefix) {
        Map<String,InstrumentDef> result = new HashMap<>();
        List<Map> intrumentConfigs = (List<Map>)ConfigUtil.getObject(configPrefix+ITEM_INSTRUMENTS);
        if (null!=intrumentConfigs) {
            for(Map config:intrumentConfigs) {
                Exchangeable instrument = Exchangeable.fromString((String)config.get("id"));
                InstrumentDef def = new InstrumentDef(instrument, config);
                result.put(def.key, def);
            }
        }
        return result;
    }

    private void buildAccessors() {
        for(Exchangeable e: mdService.getSubscriptions()) {
            String key = InstrumentDef.instrument2key(e);
            InstrumentDef def = instrumentDefs.get(key);
            if ( def==null ) {
                continue;
            }
            BarAccessImpl accessor = new BarAccessImpl(beansContainer, data, e, def);
            accessors.put(e, accessor);
        }
    }

    private BarAccessImpl buildTechAccess(Exchangeable instrument) {
        BarAccessImpl result = accessors.get(instrument);
        if ( result==null) {
            String key = instrument.contract()+"."+instrument.exchange().name();
            InstrumentDef instrumentDef = instrumentDefs.get(key);
            if (null==instrumentDef ) {
                Map<String, Object> defConfig = new HashMap<>();
                instrumentDef = new InstrumentDef(instrument, defConfig);
            }
            if ( instrumentDef!=null ) {
                result = new BarAccessImpl(beansContainer, data, instrument, instrumentDef);
                if ( instrument.getType()==ExchangeableType.FUTURE_COMBO) {
                    FutureCombo combo = (FutureCombo)instrument;
                    accessors.put(combo.getExchangeable1(), result);
                    mdService.addListener(this, combo.getExchangeable1());
                    accessors.put(combo.getExchangeable2(), result);
                    mdService.addListener(this, combo.getExchangeable2());
                } else {
                    mdService.addListener(this, instrument);
                }
                accessors.put(instrument, result);
                logger.info("Register new instrument "+instrument);
            }
        }
        return result;
    }

}
