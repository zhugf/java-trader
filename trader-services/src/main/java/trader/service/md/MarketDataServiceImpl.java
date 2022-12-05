package trader.service.md;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import trader.common.beans.BeansContainer;
import trader.common.beans.ServiceEvent;
import trader.common.beans.ServiceEventHub;
import trader.common.beans.ServiceState;
import trader.common.config.ConfigService;
import trader.common.config.ConfigUtil;
import trader.common.exception.AppException;
import trader.common.exchangeable.Exchange;
import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableType;
import trader.common.exchangeable.ExchangeableUtil;
import trader.common.exchangeable.Future;
import trader.common.util.ConversionUtil;
import trader.common.util.FileUtil;
import trader.common.util.JsonUtil;
import trader.common.util.StringUtil;
import trader.common.util.TraderHomeUtil;
import trader.service.ServiceConstants.AccountState;
import trader.service.ServiceConstants.ConnState;
import trader.service.ServiceErrorCodes;
import trader.service.event.AsyncEvent;
import trader.service.event.AsyncEventService;
import trader.service.md.ctp.CtpMarketDataProducerFactory;
import trader.service.md.spi.AbsMarketDataProducer;
import trader.service.md.spi.MarketDataProducerListener;
import trader.service.md.web.WebMarketDataProducerFactory;
import trader.service.plugin.Plugin;
import trader.service.plugin.PluginService;
import trader.service.stats.StatsCollector;
import trader.service.stats.StatsItem;
import trader.service.trade.Account;
import trader.service.trade.MarketTimeService;
import trader.service.trade.TradeConstants.TradeServiceType;
import trader.service.trade.TradeService;
import trader.service.trade.TradeServiceListener;

/**
 * 行情数据的接收和聚合
 */
@Service
public class MarketDataServiceImpl implements MarketDataService, ServiceErrorCodes {
    private final static Logger logger = LoggerFactory.getLogger(MarketDataServiceImpl.class);
    /**
     * 是否保存行情数据
     */
    public static final String ITEM_SAVE_DATA = "saveData";
    /**
     * 是否保存合并后的行情数据
     */
    public static final String ITEM_SAVE_MERGED = "saveMerged";
    /**
     * 行情数据源定义
     */
    public static final String ITEM_PRODUCERS = "producer[]";

    /**
     * 主动订阅的品种
     */
    public static final String ITEM_SUBSCRIPTIONS = "subscriptions";

    /**
     * 主动订阅的品种
     */
    public static final String ITEM_SUBSCRIPTION_BY_TYPES = "subscriptionByTypes";

    /**
     * Producer连接超时设置: 15秒
     */
    public static final int PRODUCER_CONNECTION_TIMEOUT = 15*1000;

    public static final String FILE_INSTRUMENT_OPENINTS = "instrumentOpenInts.json";

    @Autowired
    private BeansContainer beansContainer;

    @Autowired
    private StatsCollector statsCollector;

    @Autowired
    private ConfigService configService;

    @Autowired
    private ExecutorService executorService;

    @Autowired
    private ScheduledExecutorService scheduledExecutorService;

    @Autowired
    private PluginService pluginService;

    @Autowired
    private AsyncEventService asyncEventService;

    @Autowired
    private MarketTimeService mtService;

    private ServiceState state = ServiceState.NotInited;

    private MarketDataSaver dataSaver;

    private boolean saveData;
    private boolean saveMerged;

    private Map<String, MarketDataProducerFactory> producerFactories;

    private List<Exchangeable> primaryInstruments = new ArrayList<>();

    private List<Exchangeable> allInstruments = new ArrayList<>();

    /**
     * 采用copy-on-write多线程访问方式，可以不使用锁
     */
    private Map<String, AbsMarketDataProducer> producers = new HashMap<>();

    private List<MarketDataListener> genericListeners = new ArrayList<>();

    /**
     * 使用Copy-On-Write维护的行情读写锁
     */
    private Map<Exchangeable, MarketDataRuntimeData> instrumentRuntimes = new ConcurrentHashMap<>();
    private ReentrantLock instrumentRuntimesLock = new ReentrantLock();
    private AtomicLong totalTicksRecv = new AtomicLong();

    @PostConstruct
    public void init() {
        ServiceEventHub serviceEventHub = beansContainer.getBean(ServiceEventHub.class);
        serviceEventHub.registerServiceInitializer(getClass().getName(), ()->{
            return init0();
        }, pluginService, asyncEventService);
        serviceEventHub.addListener((ServiceEvent event)->{
            onAllServiceInited();
        }, ServiceEventHub.TOPIC_SERVICE_ALL_INIT_DONE);
    }

    private MarketDataService init0() {
        state = ServiceState.Starting;
        producerFactories = discoverProducerProviders(beansContainer);
        queryOrLoadPrimaryInstruments();
        List<Exchangeable> allInstruments = reloadSubscriptions(Collections.emptyList(), null);
        logger.info("Subscrible instruments: "+allInstruments);
        String configPrefix = MarketDataService.class.getSimpleName()+".";
        saveData = ConfigUtil.getBoolean(configPrefix+ITEM_SAVE_DATA, true);
        saveMerged = ConfigUtil.getBoolean(configPrefix+ITEM_SAVE_MERGED, true);
        if ( saveData ) {
            dataSaver = new MarketDataSaver(beansContainer);
        } else {
            logger.info("MarketDataServie save data is disabled.");
        }
        asyncEventService.addFilter(AsyncEventService.FILTER_CHAIN_MD, (AsyncEvent event)->{
            onAsyncEvent(event);
            return true;
        }, AsyncEvent.EVENT_TYPE_MARKETDATA_MASK);

        reloadProducers(configPrefix);
        TradeService tradeService = beansContainer.getBean(TradeService.class);
        if ( null!=tradeService && tradeService.getType()==TradeServiceType.RealTime ) {
            tradeService.addListener(new TradeServiceListener() {
            public void onAccountStateChanged(Account account, AccountState oldState) {
                if ( account.getState()==AccountState.Ready ) {
                    queryAccountInstruments(account);
                }
            }});
        }

        //启动结束, 注册定时任务等
        statsCollector.registerStatsItem(new StatsItem(MarketDataService.class.getSimpleName(), "currInstruments"),  (StatsItem itemInfo) -> {
            return instrumentRuntimes.size();
        });
        statsCollector.registerStatsItem(new StatsItem(MarketDataService.class.getSimpleName(), "totalTicksRecv"),  (StatsItem itemInfo) -> {
            return totalTicksRecv.get();
        });

        //当行情订阅有变化时得到通知
        configService.addListener(new String[] {ITEM_SUBSCRIPTIONS}, (path, newValue)->{
            reloadSubscriptionsAndSubscribe();
        });
        state = ServiceState.Ready;
        return this;
    }

    /**
     * 启动后, 连接行情数据源
     */
    private void onAllServiceInited(){
        executorService.execute(()->{
            for(AbsMarketDataProducer p:producers.values()) {
                if ( p.getState()==ConnState.Initialized ) {
                    p.connect();
                }
            }
        });
        //每隔1min, 保存一次全市场合约的成交/持仓量
        scheduledExecutorService.scheduleAtFixedRate(()->{
            if ( ServiceState.Ready==state ) {
                saveInstrumentOpenInts();
            }
        }, 1, 1, TimeUnit.MINUTES);
    }

    @PreDestroy
    public void destroy() {
        state = ServiceState.Stopped;
        if ( null!=this.dataSaver ) {
            dataSaver.flushAllWriters(true);
        }
        for(AbsMarketDataProducer producer:producers.values()) {
            logger.info(producer.getId()+" state="+producer.getState()+", connectCount="+producer.getConnectCount()+", tickCount="+producer.getTickCount());
        }
    }

    @Override
    public ServiceState getState() {
        return state;
    }

    @Override
    public Map<String, MarketDataProducerFactory> getProducerFactories(){
        return Collections.unmodifiableMap(producerFactories);
    }

    @Override
    public Exchangeable getPrimaryInstrument(Exchange exchange, String commodity) {
        int occurence=0;
        char cc = commodity.charAt(commodity.length()-1);
        if ( cc>='0' && cc<='9') {
            occurence = cc-'0';
            commodity = commodity.substring(0, commodity.length()-1);
        }
        if ( exchange==null ) {
            exchange = Future.detectExchange(commodity);
        }
        int instrumentOccurence=0;
        Exchangeable primaryInstrument=null;
        for(Exchangeable pi:primaryInstruments) {
            if ( pi.exchange()==exchange && pi.contract().equalsIgnoreCase(commodity) ) {
                instrumentOccurence++;
                if ( instrumentOccurence>=occurence ) {
                    primaryInstrument = pi;
                    break;
                }
            }
        }
        return primaryInstrument;
    }

    @Override
    public Collection<MarketDataProducer> getProducers() {
        List<MarketDataProducer> result = new LinkedList<>();
        result.addAll(producers.values());
        return result;
    }

    @Override
    public MarketDataProducer getProducer(String producerId) {
        return producers.get(producerId);
    }

    @Override
    public MarketData getLastData(Exchangeable e) {
        MarketDataRuntimeData holder = instrumentRuntimes.get(e);
        if ( holder!=null ) {
            return holder.getLastData();
        }
        return null;
    }

    @Override
    public void addSubscriptions(Collection<Exchangeable> subscriptions) {
        List<Exchangeable> newSubscriptions = new ArrayList<>();
        for(Exchangeable e:subscriptions) {
            if ( instrumentRuntimes.containsKey(e) ) {
                continue;
            }
            newSubscriptions.add(e);
            getOrCreateListenerHolder(e, true, newSubscriptions);
        }
        if ( !newSubscriptions.isEmpty() && ServiceState.Stopped!=state ) {
            producersSubscribe(newSubscriptions);
        }
    }

    @Override
    public Collection<Exchangeable> getSubscriptions(){
        return new ArrayList<>(instrumentRuntimes.keySet());
    }

    @Override
    public void addListener(MarketDataListener listener, Exchangeable... instruments) {
        List<Exchangeable> subscribes = new ArrayList<>();
        if ( instruments==null || instruments.length==0 || (instruments.length==1&&instruments[0]==null) ){
            genericListeners.add(listener);
        } else {
            for(Exchangeable exchangeable:instruments) {
                MarketDataRuntimeData holder = getOrCreateListenerHolder(exchangeable, true, subscribes);
                holder.addListener(listener);
            }
        }
        //从行情服务器订阅新的品种
        if ( subscribes.size()>0 ) {
            executorService.execute(()->{
                producersSubscribe(subscribes);
            });
        }
    }

    /**
     * 响应状态改变, 订阅行情
     */
    private void onProducerStateChanged(AbsMarketDataProducer producer) {
        switch(producer.getState()) {
        case Connected:
            Collection<Exchangeable> instruments = allInstruments; //getSubscriptions();
            if ( instruments.size()>0 ) {
                executorService.execute(()->{
                    producer.subscribe(instruments);
                });
            }
            break;
        case Disconnected:
            AppException ap = new AppException(ERR_MD_PRODUCER_DISCONNECTED, "Producer "+producer.getId()+" is disconnected.");
            logger.warn(ap.getMessage());
            break;
        case ConnectFailed:
            AppException ap2 = new AppException(ERR_MD_PRODUCER_CONNECT_FAILED, "Producer "+producer.getId()+" is connect failed.");
            logger.warn(ap2.getMessage());
            break;
        default:
            break;
        }
    }

    /**
     * 排队行情事件到disruptor的事件句柄, 再由 onAsyncEvent 处理
     */
    private void onProducerMarketData(MarketData md) {
        asyncEventService.publishEvent(AsyncEvent.EVENT_TYPE_MARKETDATA, null, md,  null);
        if ( saveData ) {
            dataSaver.asyncSave(md);
        }
    }

    /**
     * 处理MarketData事件
     */
    private void onAsyncEvent(AsyncEvent event)
    {
        MarketData tick = (MarketData)event.data;
        //如果行情时间和系统时间差距超过2小时, 忽略.
        if ( Math.abs(mtService.currentTimeMillis()-tick.updateTimestamp)>= 2*3600*1000 ) {
            if ( logger.isDebugEnabled()) {
                logger.debug("Ignore market data: "+tick);
            }
        }
        totalTicksRecv.incrementAndGet();
        MarketDataRuntimeData holder= getOrCreateListenerHolder(tick.instrument, true, null);
        if ( null!=holder && holder.checkTick(tick) ) {
            tick.postProcess(holder.getTradingTimes());
            //通用Listener
            for(int i=0;i<genericListeners.size();i++) {
                try{
                    genericListeners.get(i).onMarketData(tick);
                }catch(Throwable t) {
                    logger.error("Marketdata listener "+genericListeners.get(i)+" process failed: "+tick,t);
                }
            }
            //特有的listeners
            List<MarketDataListener> listeners = holder.getListeners();
            for(int i=0;i<listeners.size();i++) {
                try {
                    listeners.get(i).onMarketData(tick);
                }catch(Throwable t) {
                    logger.error("Marketdata listener "+listeners.get(i)+" process failed: "+tick,t);
                }
            }
            //保存合并后的MarketData
            if ( saveMerged && saveData ) {
                MarketData tick0 = tick.clone();
                tick0.producerId = "merged";
                dataSaver.asyncSave(tick0);
            }
        }
    }

    static class FutureInfo{
        Future instrument;
        long volume;
        long openInt;
    }

    /**
     * 实时查询主力合约, 失败则加载上一次的值
     */
    private void queryOrLoadPrimaryInstruments() {
        //查询主力合约
        try {
            File instrumentRuntimes = new File( TraderHomeUtil.getDirectory(TraderHomeUtil.DIR_WORK), FILE_INSTRUMENT_OPENINTS);
            if ( instrumentRuntimes.exists() ) {
                JsonObject openIntsByInstrument = JsonParser.parseString(FileUtil.read(instrumentRuntimes)).getAsJsonObject();
                Map<String, List<FutureInfo>> futureByContracts = new HashMap<>();
                for(String key:openIntsByInstrument.keySet()) {
                    JsonObject json = openIntsByInstrument.get(key).getAsJsonObject();
                    FutureInfo info = new FutureInfo();
                    info.instrument = (Future)Exchangeable.fromString(key);
                    info.volume = ConversionUtil.toInt(json.get("volume").getAsString());
                    info.openInt = ConversionUtil.toInt(json.get("openInt").getAsString());
                    List<FutureInfo> futures = futureByContracts.get(info.instrument.contract());
                    if (null==futures) {
                        futures = new ArrayList<>();
                        futureByContracts.put(info.instrument.contract(), futures);
                    }
                    futures.add(info);
                }
                //排序之后再确定选择: 交易第一位
                for(List<FutureInfo> infos:futureByContracts.values()) {
                    Collections.sort(infos, (FutureInfo o1, FutureInfo o2)->{
                        return (int)(o1.volume - o2.volume);
                    });
                    FutureInfo info0 = infos.get(infos.size()-1);
                    if( info0.openInt>0 && !primaryInstruments.contains(info0.instrument)) {
                        primaryInstruments.add(info0.instrument);
                    }
                }
                allInstruments = (List)Future.buildAllInstruments(mtService.getTradingDay());
            }
        }catch(Throwable t) {
            logger.warn("查询主力合约失败", t);
        }
    }

    /**
     * 为行情服务器订阅品种
     */
    private void producersSubscribe(List<Exchangeable> instruments) {
        if ( instruments.isEmpty() || ServiceState.Stopped!=state ) {
            return;
        }
        List<String> connectedIds = new ArrayList<>();
        List<AbsMarketDataProducer> connectedProducers = new ArrayList<>();
        for(AbsMarketDataProducer producer:producers.values()) {
            if ( producer.getState()!=ConnState.Connected ) {
                continue;
            }
            connectedIds.add(producer.getId());
            connectedProducers.add(producer);
        }

        if (logger.isInfoEnabled()) {
            logger.info("Subscribe instruments " + instruments + " to producers: " + connectedIds);
        }

        for(AbsMarketDataProducer producer:connectedProducers) {
            producer.subscribe(instruments);
        }
    }

    /**
     * 清理连接超时的Producers
     */
    private void reconnectProducers() {
        for(AbsMarketDataProducer p:producers.values()) {
            if ( p.getState()==ConnState.Disconnected ) {
                p.connect();
            }
        }
        //断开连接超时的Producer
        for(AbsMarketDataProducer p:producers.values()) {
            if ( p.getState()==ConnState.Connecting && (System.currentTimeMillis()-p.getStateTime())>PRODUCER_CONNECTION_TIMEOUT) {
                p.close();
            }
        }
    }

    /**
     * 重新加载合约
     *
     * @param newInstruments 修改变量, 新整合约
     * @return 所有合约
     */
    private List<Exchangeable> reloadSubscriptions(Collection<Exchangeable> currInstruments, List<Exchangeable> newInstruments) {
        String text = StringUtil.trim(ConfigUtil.getString(ITEM_SUBSCRIPTIONS));
        String[] instrumentIds = StringUtil.split(text, ",|;|\r|\n");
        if ( newInstruments==null) {
            newInstruments = new ArrayList<>();
        }
        List<Exchangeable> allInstrumentsToSub = new ArrayList<>(currInstruments);

        Set<Exchangeable> resolvedInstruments = new TreeSet<>();
        for(String instrumentId:instrumentIds) {
            if ( instrumentId.startsWith("$") ) {
                if ( instrumentId.equalsIgnoreCase("$PrimaryContracts") || instrumentId.equalsIgnoreCase("$PrimaryInstruments")) {
                    resolvedInstruments.addAll(primaryInstruments);
                    continue;
                } else if ( instrumentId.equalsIgnoreCase("$AllInstruments") ) {
                    resolvedInstruments.addAll(this.allInstruments);
                    continue;
                } else {
                    //$j, $AP, $au这种, 需要解析为主力合约
                    String commodity = instrumentId.substring(1);
                    Exchangeable primaryInstrument = getPrimaryInstrument(null, commodity);
                    if ( primaryInstrument!=null ) {
                        resolvedInstruments.add(primaryInstrument);
                    }else {
                        logger.warn("解析主力合约失败: "+instrumentId);
                    }
                }
            } else {
                Exchangeable e = Exchangeable.fromString(instrumentId);
                resolvedInstruments.add(e);
            }
        }
        for(Exchangeable e:resolvedInstruments) {
            if ( allInstrumentsToSub.contains(e)) {
                continue;
            }
            allInstrumentsToSub.add(e);
            newInstruments.add(e);
        }
        String message = "Total "+allInstrumentsToSub.size()+" subscriptions loaded, "+newInstruments.size()+" added";
        if ( newInstruments.size()>0 ) {
            logger.info(message);
            for(Exchangeable e:newInstruments) {
                getOrCreateListenerHolder(e, true, null);
            }
        }else {
            logger.debug(message);
        }
        return allInstrumentsToSub;
    }

    /**
     * 重新加载并主动订阅
     */
    private void reloadSubscriptionsAndSubscribe() {
        List<Exchangeable> newInstruments = new ArrayList<>();
        reloadSubscriptions(instrumentRuntimes.keySet(), newInstruments);
        if ( !newInstruments.isEmpty() ) {
            producersSubscribe(newInstruments);
        }
    }

    /**
     * 重新加载配置, 检查配置变化
     */
    private void reloadProducers(String configPrefix) {
        long t0 = System.currentTimeMillis();
        Map<String, AbsMarketDataProducer> currProducers = new HashMap<>(this.producers);
        Map<String, AbsMarketDataProducer> newProducers = new LinkedHashMap<>();
        List<Map> producerConfigs = (List<Map>)ConfigUtil.getObject(configPrefix+ITEM_PRODUCERS);
        List<String> newProducerIds = new ArrayList<>();
        List<String> delProducerIds = new ArrayList<>();
        if ( null!=producerConfigs ) {
            for(Map producerConfig:producerConfigs) {
                String id = (String)producerConfig.get("id");
                AbsMarketDataProducer currProducer = currProducers.remove(id);
                if ( currProducer!=null ) {
                    if ( currProducer.configEquals(producerConfig) ) {
                        //没有变化
                        newProducers.put(id, currProducer);
                    } else {
                        //发生变化, 删除已有, 再创建新的
                        currProducer.close();
                        delProducerIds.add(id);
                        currProducer = null;
                    }
                }
                if ( null==currProducer ) {
                    try{
                        currProducer = createMarketDataProducer(producerConfig);
                        newProducerIds.add(id);
                        newProducers.put(id, currProducer);
                    }catch(Throwable t) {
                        logger.error("Create market data producer "+id+" from config failed: "+producerConfig, t);
                    }
                }
            }
        }
        for(AbsMarketDataProducer oldProducer:currProducers.values()) {
            oldProducer.close();
            delProducerIds.add(oldProducer.getId());
        }
        this.producers = newProducers;
        long t1 = System.currentTimeMillis();
        String message = "Total " + producers.size() + " producers loaded from " + producerConfigs.size() + " config items in " + (t1 - t0) + " ms, added: " + newProducerIds + ", deleted: " + delProducerIds;
        if (newProducerIds.size() > 0 || delProducerIds.size() > 0) {
            logger.info(message);
        } else {
            logger.debug(message);
        }
        if ( ServiceState.Stopped!=state ) {
            for(AbsMarketDataProducer p:newProducers.values()) {
                p.connect();
            }
        }
    }

    private AbsMarketDataProducer createMarketDataProducer(Map producerConfig) throws AppException
    {
        String id = (String)producerConfig.get("id");
        AbsMarketDataProducer result = null;
        String provider = ConversionUtil.toString(producerConfig.get("provider"));
        if (StringUtil.isEmpty(provider)) {
            provider = MarketDataProducer.PROVIDER_CTP;
        }
        if ( producerFactories.containsKey(provider) ){
            result = (AbsMarketDataProducer)producerFactories.get(provider).create(beansContainer, producerConfig);
            result.setListener(new MarketDataProducerListener() {
                public void onStateChanged(AbsMarketDataProducer producer, ConnState lastStatus) {
                    onProducerStateChanged(producer);
                }
                public void onMarketData(MarketData md) {
                    onProducerMarketData(md);
                }
            });
        }
        if ( null==result ) {
            throw new AppException(ERR_MD_PRODUCER_CREATE_FAILED, "行情 "+id+" 不支持的接口类型: "+provider);
        }
        return result;
    }

    /**
     *
     * @param exchangeable 交易所品种
     * @param autoCreate 是否自动创建?
     * @param newSubscribes 待订阅的行情
     * @return
     */
    private MarketDataRuntimeData getOrCreateListenerHolder(Exchangeable exchangeable, boolean autoCreate, List<Exchangeable> newSubscribes) {
        MarketDataRuntimeData holder = instrumentRuntimes.get(exchangeable);
        if (null == holder && autoCreate) {
            while(!instrumentRuntimesLock.tryLock());
            try {
                holder = instrumentRuntimes.get(exchangeable);
                if ( null==holder ) {
                    holder = new MarketDataRuntimeData(exchangeable, mtService.getTradingDay());
                    instrumentRuntimes.put(exchangeable, holder);
                    if (newSubscribes != null) {
                        newSubscribes.add(exchangeable);
                    }
                }
            }finally {
                instrumentRuntimesLock.unlock();
            }
        }
        return holder;
    }

    /**
     * 保存全部合约的持仓量, 用于下一次启动时自动查找主力合约
     */
    private void saveInstrumentOpenInts() {
        JsonObject openIntsByInstrument = new JsonObject();
        File instrumentOpenInts = new File( TraderHomeUtil.getDirectory(TraderHomeUtil.DIR_WORK), FILE_INSTRUMENT_OPENINTS);
        //加载上一次保存数据并自动删除过期的合约
        if ( instrumentOpenInts.exists() ) {
            try{
                openIntsByInstrument = JsonParser.parseString(FileUtil.read(instrumentOpenInts)).getAsJsonObject();
                Set<Future> allFutures = new HashSet<>(Future.buildAllInstruments(mtService.getTradingDay()));
                //删除过期合约
                for(String key:new ArrayList<>(openIntsByInstrument.keySet())) {
                    Exchangeable instrument = Exchangeable.fromString(key);
                    if ( !allFutures.contains(instrument) ) {
                        openIntsByInstrument.remove(key);
                    }
                }
            }catch(Throwable t) {}
        }
        for(MarketDataRuntimeData runtimeData:instrumentRuntimes.values()) {
            JsonObject json = new JsonObject();
            json.addProperty("instrument", runtimeData.getInstrument().uniqueId());
            MarketData md = runtimeData.getLastData();
            if ( null==md ) {
                continue;
            }
            json.addProperty("openInt", md.openInterest);
            json.addProperty("volume", md.volume);
            openIntsByInstrument.add(runtimeData.getInstrument().uniqueId(), json);
        }
        try{
            FileUtil.save(instrumentOpenInts, JsonUtil.json2str(openIntsByInstrument, false));
        }catch(Throwable t) {
            logger.error("保存合约持仓量信息失败", t);
        }
    }

    public static Map<String, MarketDataProducerFactory> discoverProducerProviders(BeansContainer beansContainer ){
        Map<String, MarketDataProducerFactory> result = new TreeMap<>();

        result.put(MarketDataProducer.PROVIDER_CTP, new CtpMarketDataProducerFactory());
        result.put(MarketDataProducer.PROVIDER_WEB, new WebMarketDataProducerFactory());

        PluginService pluginService = beansContainer.getBean(PluginService.class);
        if (pluginService!=null) {
            for(Plugin plugin : pluginService.search(Plugin.PROP_EXPOSED_INTERFACES + "=" + MarketDataProducerFactory.class.getName())) {
                Map<String, MarketDataProducerFactory> pluginProducerFactories = plugin.getBeansOfType(MarketDataProducerFactory.class);
                result.putAll(pluginProducerFactories);
            }
        }

        return result;
    }

    private void queryAccountInstruments(Account account) {
        String subscriptionByTypes = ConfigUtil.getString(ITEM_SUBSCRIPTION_BY_TYPES);
        List<ExchangeableType> types = new ArrayList<>();
        String[] subTypes = StringUtil.split(subscriptionByTypes, ",");
        for(int i=0;i<subTypes.length;i++) {
            types.add(ConversionUtil.toEnum(ExchangeableType.class, subTypes[i]));
        }
        Set<Exchangeable> instrumentsToSub = new TreeSet<>();
        if ( types.size()>0 ) {
            try {
                Collection<Exchangeable> accountInstruments = account.getSession().syncQueryInstruments();
                for(Exchangeable e:accountInstruments) {
                    if ( types.contains(e.getType()) && !instrumentRuntimes.containsKey(e)) {
                        instrumentsToSub.add(e);
                    }
                }
            }catch(Throwable t) {}
        }
        if ( !instrumentsToSub.isEmpty() ) {
            logger.info("订阅 "+subscriptionByTypes+" 分类合约: "+instrumentsToSub);
            addSubscriptions(instrumentsToSub);
        }
    }

}
