package trader.service.ta;

import java.sql.Connection;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.PreDestroy;
import javax.sql.DataSource;

import org.aspectj.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import trader.common.beans.BeansContainer;
import trader.common.beans.ServiceState;
import trader.common.config.ConfigUtil;
import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableData;
import trader.common.util.StringUtil;
import trader.common.util.TraderHomeUtil;
import trader.service.data.KVStore;
import trader.service.data.KVStoreService;
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

    private DataSource ds;

    private KVStore kvStore;

    @Override
    public void init(BeansContainer beansContainer) {
        state = ServiceState.Starting;
        this.beansContainer = beansContainer;
        KVStoreService kvStoreService = beansContainer.getBean(KVStoreService.class);
        if ( kvStoreService!=null ) {
            kvStore = kvStoreService.getStore(null);
        }
        ds = beansContainer.getBean(DataSource.class);
        if ( ds!=null ) {
            try(Connection conn=ds.getConnection();){
                loadRepositorySql(conn);
            } catch(Throwable t) {
                logger.error("Init repository tables failed");
            }
        }
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
    public void registerListener(List<Exchangeable> instruments, TechnicalAnalysisListener listener) {
        for(Exchangeable instrument:instruments) {
            TechnicalAnalysisAccessImpl accessImpl = accessors.get(instrument);
            if ( accessImpl==null) {
                String key = instrument.commodity()+"."+instrument.exchange().name();
                InstrumentDef instrumentDef = instrumentDefs.get(key);
                if ( instrumentDef!=null ) {
                    accessImpl = new TechnicalAnalysisAccessImpl(beansContainer, data, instrument, instrumentDef);
                    accessors.put(instrument, accessImpl);
                    mdService.addListener(this, instrument);
                    logger.info("Register new instrument "+instrument);
                }
            }
            if ( accessImpl!=null ) {
                accessImpl.registerListener(listener);
            }
        }
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

    public static void loadRepositorySql(Connection conn) throws Exception
    {
        byte[] data = FileUtil.readAsByteArray(TechnicalAnalysisServiceImpl.class.getClassLoader().getResourceAsStream("/repository.sql"));
        String text = new String(data, StringUtil.UTF8);

        try(Statement stmt=conn.createStatement();){
            StringBuilder sql = new StringBuilder();
            for(String line:StringUtil.text2lines(text, true, true)) {
                if ( line.startsWith("--")) {
                    continue;
                }
                if ( sql.length()>0 ) {
                    sql.append("\n");
                }
                sql.append(line);
                if ( line.endsWith(";")) {
                    stmt.execute(sql.toString());
                    sql.setLength(0);;
                }
            }
        }
    }

}
