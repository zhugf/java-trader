package trader.service.ta;

import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.PreDestroy;

import org.aspectj.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import trader.common.beans.BeansContainer;
import trader.common.beans.ServiceState;
import trader.common.config.ConfigUtil;
import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableData;
import trader.common.exchangeable.ExchangeableType;
import trader.common.exchangeable.FutureCombo;
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
public class TechnicalAnalysisServiceImpl implements TechnicalAnalysisService, MarketDataListener {
    private final static Logger logger = LoggerFactory.getLogger(TechnicalAnalysisServiceImpl.class);
    /**
     * 服务状态
     */
    public static final String ITEM_STATE = "/TechnicalAnalysisService/state";
    /**
     * 关注的品种定义
     */
    public static final String ITEM_INSTRUMENTS = "/TechnicalAnalysisService/instrument[]";

    private BeansContainer beansContainer;

    private MarketDataService mdService;

    private ExchangeableData data;

    private ServiceState state = ServiceState.Unknown;

    private Map<String, InstrumentDef> instrumentDefs = new HashMap<>();

    private Map<Exchangeable, TechnicalAnalysisAccessImpl> accessors = new HashMap<>();

    @Override
    public void init(BeansContainer beansContainer) {
        state = ServiceState.Starting;
        this.beansContainer = beansContainer;
        data = TraderHomeUtil.getExchangeableData();
        mdService = beansContainer.getBean(MarketDataService.class);
        mdService.addListener(this);
        instrumentDefs.putAll( loadInstrumentDefs());
        buildAccessors();
        logger.info("Start with data dir "+data.getDataDir());
        state = ServiceState.Ready;
    }

    @Override
    @PreDestroy
    public void destroy() {
    }

    @Override
    public TechnicalAnalysisAccess forInstrument(Exchangeable instrument) {
        return accessors.get(instrument);
    }

    @Override
    public Collection<Exchangeable> getInstruments(){
        return new ArrayList<>(accessors.keySet());
    }

    @Override
    public boolean registerListener(List<Exchangeable> instruments, TechnicalAnalysisListener listener) {
        boolean result = false;
        for(Exchangeable instrument:instruments) {
            TechnicalAnalysisAccessImpl accessImpl = buildTechAccess(instrument);
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
            TechnicalAnalysisAccessImpl accessor = accessors.get(tick.instrument);
            if ( accessor!=null ) {
                accessor.onMarketData(tick);
            }
        }
    }

    public void addInstrumentDef(InstrumentDef instrumentDef) {
        instrumentDefs.put(instrumentDef.key, instrumentDef);
    }

    private Map<String,InstrumentDef> loadInstrumentDefs() {
        Map<String,InstrumentDef> result = new HashMap<>();
        List<Map> intrumentConfigs = (List<Map>)ConfigUtil.getObject(ITEM_INSTRUMENTS);
        for(Map config:intrumentConfigs) {
            Exchangeable instrument = Exchangeable.fromString((String)config.get("id"));
            InstrumentDef def = new InstrumentDef(instrument, config);
            result.put(def.key, def);
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
            TechnicalAnalysisAccessImpl accessor = new TechnicalAnalysisAccessImpl(beansContainer, data, e, def);
            accessors.put(e, accessor);
        }
    }

    private TechnicalAnalysisAccessImpl buildTechAccess(Exchangeable instrument) {
        TechnicalAnalysisAccessImpl result = accessors.get(instrument);
        if ( result==null) {
            String key = instrument.commodity()+"."+instrument.exchange().name();
            InstrumentDef instrumentDef = instrumentDefs.get(key);
            if ( instrumentDef!=null ) {
                result = new TechnicalAnalysisAccessImpl(beansContainer, data, instrument, instrumentDef);
                if ( instrument.getType()==ExchangeableType.FUTURE_COMBO) {
                    FutureCombo combo = (FutureCombo)instrument;
                    accessors.put(combo.getExchangeable1(), result);
                    mdService.addListener(this, combo.getExchangeable1());
                    accessors.put(combo.getExchangeable2(), result);
                    mdService.addListener(this, combo.getExchangeable2());
                } else {
                    accessors.put(instrument, result);
                    mdService.addListener(this, instrument);
                }
                logger.info("Register new instrument "+instrument);
            }
        }
        return result;
    }

}
