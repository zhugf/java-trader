package trader.service.md;

import java.io.File;
import java.net.URL;
import java.net.URLConnection;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import trader.common.beans.BeansContainer;
import trader.common.beans.ServiceState;
import trader.common.config.ConfigService;
import trader.common.config.ConfigUtil;
import trader.common.exception.AppException;
import trader.common.exchangeable.Exchange;
import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.Future;
import trader.common.util.ConversionUtil;
import trader.common.util.DateUtil;
import trader.common.util.FileUtil;
import trader.common.util.IOUtil;
import trader.common.util.StringUtil;
import trader.common.util.TraderHomeUtil;
import trader.service.ServiceConstants.ConnState;
import trader.service.ServiceErrorCodes;
import trader.service.event.AsyncEvent;
import trader.service.event.AsyncEventFilter;
import trader.service.event.AsyncEventService;
import trader.service.md.ctp.CtpMarketDataProducerFactory;
import trader.service.md.spi.AbsMarketDataProducer;
import trader.service.md.spi.MarketDataProducerListener;
import trader.service.plugin.Plugin;
import trader.service.plugin.PluginService;
import trader.service.trade.MarketTimeService;

/**
 * 行情数据的接收和聚合
 */
@Service
public class MarketDataServiceImpl implements MarketDataService, ServiceErrorCodes, MarketDataProducerListener, AsyncEventFilter {
    private final static Logger logger = LoggerFactory.getLogger(MarketDataServiceImpl.class);
    /**
     * 是否保存行情数据
     */
    public static final String ITEM_SAVE_DATA = "/MarketDataService/saveData";
    /**
     * 行情数据源定义
     */
    public static final String ITEM_PRODUCERS = "/MarketDataService/producer[]";

    /**
     * 主动订阅的品种
     */
    public static final String ITEM_SUBSCRIPTIONS = "/MarketDataService/subscriptions";

    /**
     * Producer连接超时设置: 15秒
     */
    public static final int PRODUCER_CONNECTION_TIMEOUT = 15*1000;

    @Autowired
    private BeansContainer beansContainer;

    @Autowired
    private ConfigService configService;

    @Autowired
    private ExecutorService executorService;

    @Autowired
    private ScheduledExecutorService scheduledExecutorService;

    @Autowired
    private AsyncEventService asyncEventService;

    @Autowired
    private MarketTimeService mtService;

    private volatile boolean reloadInProgress = false;

    private ServiceState state = ServiceState.Unknown;

    private MarketDataSaver dataSaver;

    private boolean saveData;

    private Map<String, MarketDataProducerFactory> producerFactories;

    private List<Exchangeable> primaryInstruments = new ArrayList<>();

    private List<Exchangeable> primaryInstruments2 = new ArrayList<>();

    /**
     * 采用copy-on-write多线程访问方式，可以不使用锁
     */
    private Map<String, AbsMarketDataProducer> producers = new HashMap<>();

    private List<MarketDataListener> genericListeners = new ArrayList<>();

    /**
     * 使用Copy-On-Write维护的行情读写锁
     */
    private Map<Exchangeable, MarketDataListenerHolder> listenerHolders = new HashMap<>();

    private ReadWriteLock listenerHolderLock = new ReentrantReadWriteLock();

    @Override
    public void init(BeansContainer beansContainer) {
        state = ServiceState.Starting;
        producerFactories = discoverProducerProviders(beansContainer);
        queryOrLoadPrimaryInstruments();
        List<Exchangeable> allInstruments = reloadSubscriptions(Collections.emptyList(), null);
        logger.info("Subscrible instruments: "+allInstruments);

        configService.addListener(null, new String[] {ITEM_SUBSCRIPTIONS}, (source, path, newValue)->{
            reloadSubscriptionsAndSubscribe();
        });
        reloadProducers();
        scheduledExecutorService.scheduleAtFixedRate(()->{
            if ( dataSaver!=null ) {
                dataSaver.flushAllWriters();
            }
            if ( reloadInProgress ) {
                return;
            }
            try {
                reloadInProgress = true;
                reloadProducers();
                reconnectProducers();
            }finally {
                reloadInProgress = false;
            }
        }, 15, 15, TimeUnit.SECONDS);

        saveData = ConfigUtil.getBoolean(ITEM_SAVE_DATA, true);
        if ( saveData ) {
            dataSaver = new MarketDataSaver(beansContainer);
        }else {
            logger.info("MarketDataServie save data is disabled.");
        }
        asyncEventService.addFilter(AsyncEventService.FILTER_CHAIN_MAIN, this, AsyncEvent.EVENT_TYPE_MARKETDATA_MASK);
    }

    @Override
    @PreDestroy
    public void destroy() {
        state = ServiceState.Stopped;
        for(AbsMarketDataProducer producer:producers.values()) {
            logger.info(producer.getId()+" state="+producer.getState()+", connectCount="+producer.getConnectCount()+", tickCount="+producer.getTickCount());
        }
    }

    /**
     * 启动后, 连接行情数据源
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady(){
        state = ServiceState.Ready;
        executorService.execute(()->{
            for(AbsMarketDataProducer p:producers.values()) {
                if ( p.getState()==ConnState.Initialized ) {
                    p.connect();
                }
            }
        });
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
    public Collection<Exchangeable> getPrimaryInstruments(){
        return Collections.unmodifiableCollection(primaryInstruments);
    }

    @Override
    public Exchangeable getPrimaryInstrument(Exchange exchange, String commodity) {
        if ( exchange==null ) {
            exchange = Future.detectExchange(commodity);
        }
        Exchangeable primaryInstrument=null;
        for(Exchangeable pi:primaryInstruments) {
            if ( pi.exchange()==exchange && pi.commodity().equals(commodity) ) {
                primaryInstrument = pi;
                break;
            }
        }
        if ( primaryInstrument==null ) {
            for(Exchangeable pi:primaryInstruments) {
                if ( pi.exchange()==exchange && pi.commodity().equalsIgnoreCase(commodity)) {
                    primaryInstrument = pi;
                    break;
                }
            }
        }
        return primaryInstrument;
    }

    @Override
    public Collection<MarketDataProducer> getProducers() {
        var result = new LinkedList<MarketDataProducer>();
        result.addAll(producers.values());
        return result;
    }

    @Override
    public MarketDataProducer getProducer(String producerId) {
        return producers.get(producerId);
    }

    @Override
    public MarketData getLastData(Exchangeable e) {
        MarketDataListenerHolder holder = listenerHolders.get(e);
        if ( holder!=null ) {
            return holder.lastData;
        }
        return null;
    }

    @Override
    public void addSubscriptions(List<Exchangeable> subscriptions) {
        List<Exchangeable> newSubscriptions = new ArrayList<>();
        try {
            listenerHolderLock.writeLock().lock();
            for(Exchangeable e:subscriptions) {
                if ( listenerHolders.containsKey(e) ) {
                    continue;
                }
                newSubscriptions.add(e);
                createListenerHolder(e, newSubscriptions);
            }
        }finally {
            listenerHolderLock.writeLock().unlock();
        }
        if ( !newSubscriptions.isEmpty() && state==ServiceState.Ready) {
            producersSubscribe(newSubscriptions);
        }
    }

    @Override
    public Collection<Exchangeable> getSubscriptions(){
        return new ArrayList<>(listenerHolders.keySet());
    }

    @Override
    public void addListener(MarketDataListener listener, Exchangeable... exchangeables) {
        List<Exchangeable> subscribes = new ArrayList<>();
        try {
            listenerHolderLock.writeLock().lock();
            if ( exchangeables==null || exchangeables.length==0 || (exchangeables.length==1&&exchangeables[0]==null) ){
                genericListeners.add(listener);
            } else {
                for(Exchangeable exchangeable:exchangeables) {
                    MarketDataListenerHolder holder = createListenerHolder(exchangeable, subscribes);
                    holder.addListener(listener);
                }
            }
        }finally {
            listenerHolderLock.writeLock().unlock();
        }
        //从行情服务器订阅新的品种
        if ( subscribes.size()>0 ) {
            executorService.execute(()->{
                producersSubscribe(subscribes);
            });
        }
    }

    /**
     * 处理从CtpTxnSession过来的事件, 和MarketData事件
     */
    @Override
    public boolean onEvent(AsyncEvent event)
    {
        MarketData md = (MarketData)event.data;
        MarketDataListenerHolder holder= listenerHolders.get(md.instrumentId);
        if ( null!=holder && holder.checkTimestamp(md.updateTimestamp) ) {
            holder.lastData = md;
            md.postProcess(holder.getTradingTimes());
            //通用Listener
            for(int i=0;i<genericListeners.size();i++) {
                try{
                    genericListeners.get(i).onMarketData(md);
                }catch(Throwable t) {
                    logger.error("Marketdata listener "+genericListeners.get(i)+" process failed: "+md,t);
                }
            }
            //特有的listeners
            List<MarketDataListener> listeners = holder.getListeners();
            for(int i=0;i<listeners.size();i++) {
                try {
                    listeners.get(i).onMarketData(md);
                }catch(Throwable t) {
                    logger.error("Marketdata listener "+listeners.get(i)+" process failed: "+md,t);
                }
            }
        }
        return true;
    }

    /**
     * 响应状态改变, 订阅行情
     */
    @Override
    public void onStateChanged(AbsMarketDataProducer producer, ConnState oldStatus) {
        switch(producer.getState()) {
        case Connected:
            Collection<Exchangeable> exchangeables = getSubscriptions();
            if ( exchangeables.size()>0 ) {
                executorService.execute(()->{
                    producer.subscribe(exchangeables);
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
     * 排队行情事件到disruptor的事件句柄
     */
    @Override
    public void onMarketData(MarketData md) {
        asyncEventService.publishMarketData(md);
        if ( saveData ) {
            dataSaver.asyncSave(md);
        }
    }

    /**
     * 实时查询主力合约, 失败则加载上一次的值
     */
    private void queryOrLoadPrimaryInstruments() {
        //查询主力合约
        File marketDataDir = TraderHomeUtil.getDirectory(TraderHomeUtil.DIR_MARKETDATA);
        File primaryInstrumentsFile = new File(marketDataDir, "primaryInstruments.txt");
        File primaryInstruments2File = new File(marketDataDir, "primaryInstruments2.txt");

        //加载上一次的主力合约值
        List<Exchangeable> savedPrimaryInstruments = new ArrayList<>();
        List<Exchangeable> savedPrimaryInstruments2 = new ArrayList<>();

        if ( primaryInstrumentsFile.exists() && primaryInstrumentsFile.length()>0 ) {
            try{
                for(String instrument: StringUtil.text2lines(FileUtil.load(primaryInstrumentsFile), true, true)) {
                    savedPrimaryInstruments.add(Exchangeable.fromString(instrument));
                }
            }catch(Throwable t2) {}
        }
        if ( primaryInstruments2File.exists() && primaryInstruments2File.length()>0 ) {
            try{
                for(String instrument: StringUtil.text2lines(FileUtil.load(primaryInstruments2File), true, true)) {
                    savedPrimaryInstruments2.add(Exchangeable.fromString(instrument));
                }
            }catch(Throwable t2) {}
        }
        try {
            if ( queryFuturePrimaryInstruments(primaryInstruments, primaryInstruments2) ) {
                StringBuilder text = new StringBuilder();
                for(Exchangeable e:primaryInstruments) {
                    text.append(e.uniqueId()).append("\n");
                }
                //更新到硬盘, 供下次解析失败用
                FileUtil.save(primaryInstrumentsFile, text.toString());
            }
        }catch(Throwable t) {
            logger.warn("Query primary instruments failed", t);
        }
        //解析当前的主力合约失败, 使用上一次值
        if ( primaryInstruments==null || primaryInstruments.isEmpty() ) {
            primaryInstruments = savedPrimaryInstruments;
            logger.info("Reuse last primary instruments: "+savedPrimaryInstruments);
        }
        if ( primaryInstruments2==null || primaryInstruments2.isEmpty() ) {
            primaryInstruments2 = savedPrimaryInstruments2;
        }
    }

    /**
     * 为行情服务器订阅品种
     */
    private void producersSubscribe(List<Exchangeable> exchangeables) {
        if ( exchangeables.isEmpty() || state!=ServiceState.Ready ) {
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
            logger.info("Subscribe exchangeables " + exchangeables + " to producers: " + connectedIds);
        }

        for(AbsMarketDataProducer producer:connectedProducers) {
            producer.subscribe(exchangeables);
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
        List<Exchangeable> allInstruments = new ArrayList<>(currInstruments);

        Set<Exchangeable> resolvedInstruments = new TreeSet<>();
        for(String instrumentId:instrumentIds) {
            if ( instrumentId.startsWith("$")) {
                if ( instrumentId.equalsIgnoreCase("$PrimaryContracts") || instrumentId.equalsIgnoreCase("$PrimaryInstruments")) {
                    resolvedInstruments.addAll(primaryInstruments);
                    continue;
                } else if (instrumentId.equalsIgnoreCase("$PrimaryContracts2") || instrumentId.equalsIgnoreCase("$PrimaryInstruments2")) {
                    resolvedInstruments.addAll(primaryInstruments2);
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
            if ( allInstruments.contains(e)) {
                continue;
            }
            allInstruments.add(e);
            newInstruments.add(e);
        }
        String message = "Total "+allInstruments.size()+" subscriptions loaded, "+newInstruments.size()+" added";
        if ( newInstruments.size()>0 ) {
            logger.info(message);
            listenerHolderLock.writeLock().lock();
            try {
                for(Exchangeable e:newInstruments) {
                    createListenerHolder(e, null);
                }
            }finally {
                listenerHolderLock.writeLock().unlock();
            }
        }else {
            logger.debug(message);
        }
        return allInstruments;
    }

    /**
     * 重新加载并主动订阅
     */
    private void reloadSubscriptionsAndSubscribe() {
        List<Exchangeable> newInstruments = new ArrayList<>();
        reloadSubscriptions(listenerHolders.keySet(), newInstruments);
        if ( !newInstruments.isEmpty() ) {
            producersSubscribe(newInstruments);
        }
    }

    /**
     * 重新加载配置, 检查配置变化
     */
    private void reloadProducers() {
        long t0 = System.currentTimeMillis();
        var currProducers = this.producers;
        var newProducers = new HashMap<String, AbsMarketDataProducer>();
        var createdProducers = new ArrayList<AbsMarketDataProducer>();
        var producerConfigs = (List<Map>)ConfigUtil.getObject(ITEM_PRODUCERS);
        var newProducerIds = new ArrayList<String>();
        var delProducerIds = new ArrayList<String>();
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
                        createdProducers.add(currProducer);
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
        String message = "Total "+producers.size()+" producers loaded from "+producerConfigs.size()+" config items in "+(t1-t0)+" ms, added: "+newProducerIds+", deleted: "+delProducerIds;
        if ( newProducerIds.size()>0 || delProducerIds.size()>0 ) {
            logger.info(message);
        }else {
            logger.debug(message);
        }
        if ( state==ServiceState.Ready ) {
            for(AbsMarketDataProducer p:createdProducers) {
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
            result.setListener(this);
        }
        if ( null==result ) {
            throw new AppException(ERR_MD_PRODUCER_CREATE_FAILED, "行情 "+id+" 不支持的接口类型: "+provider);
        }
        return result;
    }

    private MarketDataListenerHolder createListenerHolder(Exchangeable exchangeable, List<Exchangeable> subscribes) {
        MarketDataListenerHolder holder = listenerHolders.get(exchangeable);
        if (null == holder) {
            holder = new MarketDataListenerHolder(exchangeable, mtService.getTradingDay());
            listenerHolders.put(exchangeable, holder);
            if (subscribes != null) {
                subscribes.add(exchangeable);
            }
        }
        return holder;
    }

    static class FutureInfo{
        Future future;
        long amount;
        long openInt;
    }

    /**
     * 从新浪查询主力合约, 每个品种返回持仓量和成交量最多的两个合约
     * <p>https://blog.csdn.net/dodo668/article/details/82382675
     *
     * @param primaryInstruments 主力合约
     * @param primaryInstruments2 持仓和成交量最多的两个合约
     *
     * @return true 查询成功
     */
    public static boolean queryFuturePrimaryInstruments(List<Exchangeable> primaryInstruments, List<Exchangeable> primaryInstruments2) {
        Map<String, List<FutureInfo>> futureInfos = new HashMap<>();
        Map<String, Future> futuresByName = new HashMap<>();
        LocalDate currYear = LocalDate.now();
        //构建所有的期货合约
        List<Future> allFutures = Future.buildAllInstruments(LocalDate.now());
        StringBuilder url = new StringBuilder("http://hq.sinajs.cn/list=");
        for(int i=0;i<allFutures.size();i++) {
            Future f=allFutures.get(i);
            if ( i>0 ) {
                url.append(",");
            }
            String sinaId = f.id().toUpperCase();
            if( f.contract().length()==3 ) {
                //AP901 -> AP1901, AP001->AP2001
                sinaId = f.commodity().toUpperCase()+DateUtil.date2str(currYear).substring(2, 3)+f.contract();
                String yymm = DateUtil.date2str(currYear).substring(2, 3)+f.contract();
                LocalDate contractDate = DateUtil.str2localdate(DateUtil.date2str(currYear).substring(0, 2)+yymm+"01");
                if ( contractDate.getYear()+5<currYear.getYear() ) {
                    yymm  = DateUtil.date2str(currYear.plusYears(1)).substring(2, 3)+f.contract();
                    sinaId = f.commodity().toUpperCase()+yymm;
                }
            }
            url.append(sinaId);
            futuresByName.put(sinaId, f);
        }
        for(Future future:allFutures) {
            futuresByName.put(future.id().toUpperCase(), future);
        }
        //从新浪查询期货合约
        String text = "";
        try{
            URLConnection conn = (new URL(url.toString())).openConnection();
            text = IOUtil.read(conn.getInputStream(), StringUtil.GBK);
            if ( logger.isDebugEnabled() ) {
                logger.debug("新浪合约行情: "+text);
            }
        }catch(Throwable t) {
            logger.error("获取新浪合约行情失败, URL: "+url, t);
            return false;
        }
        //分解持仓和交易数据
        Pattern contractPattern = Pattern.compile("([a-zA-Z]+)\\d+");
        for(String line:StringUtil.text2lines(text, true, true)) {
            if ( line.indexOf("\"\"")>0 ) {
                //忽略不存在的合约
                //var hq_str_TF1906="";
                continue;
            }
            try {
                line = line.substring("var hq_str_".length());
                int equalIndex=line.indexOf("=");
                int lastQuotaIndex = line.lastIndexOf('"');
                String contract = line.substring(0, equalIndex);
                String csv = line.substring(equalIndex+1, lastQuotaIndex);
                //
                String parts[] = StringUtil.split(csv, ",");
                long openInt = ConversionUtil.toLong(parts[13]);
                long amount = ConversionUtil.toLong(parts[14]);
                String commodity = null;
                Matcher matcher = contractPattern.matcher(contract);
                if ( matcher.matches() ) {
                    commodity = matcher.group(1);
                }
                Future future = futuresByName.get(contract);
                FutureInfo info = new FutureInfo();
                info.future = futuresByName.get(contract);
                info.openInt = ConversionUtil.toLong(parts[13]);
                info.amount = ConversionUtil.toLong(parts[14]);
                List<FutureInfo> infos = futureInfos.get(commodity);
                if ( infos==null ) {
                    infos = new ArrayList<>();
                    futureInfos.put(commodity, infos);
                }
                infos.add(info);
            }catch(Throwable t) {
                logger.error("Parse sina hq line failed: "+line+", exception: "+t);
            }
        }
        //排序之后再确定选择: 持仓和交易前两位
        Set<Future> primaryInstruments2Set = new TreeSet<>();
        for(List<FutureInfo> infos:futureInfos.values()) {
            Collections.sort(infos, (FutureInfo o1, FutureInfo o2)->{
                return (int)(o1.openInt - o2.openInt);
            });
            FutureInfo info = infos.get(infos.size()-1);
            if( info.openInt>0) {
                primaryInstruments2Set.add(info.future);
                primaryInstruments.add(info.future);
            }
            info = infos.get(infos.size()-2);
            if( info.openInt>0) {
                primaryInstruments2Set.add(info.future);
            }
            info = infos.get(infos.size()-3);
            if( info.openInt>0) {
                primaryInstruments2Set.add(info.future);
            }
            Collections.sort(infos, (FutureInfo o1, FutureInfo o2)->{
                return (int)(o1.amount - o2.amount);
            });
            info = infos.get(infos.size()-1);
            if (info.amount>0) {
                primaryInstruments2Set.add(info.future);
            }
            info = infos.get(infos.size()-2);
            if (info.amount>0) {
                primaryInstruments2Set.add(info.future);
            }
            info = infos.get(infos.size()-3);
            if (info.amount>0) {
                primaryInstruments2Set.add(info.future);
            }
        }
        primaryInstruments2.addAll(primaryInstruments2Set);
        return true;
    }

    public static Map<String, MarketDataProducerFactory> discoverProducerProviders(BeansContainer beansContainer ){
        Map<String, MarketDataProducerFactory> result = new TreeMap<>();

        result.put(MarketDataProducer.PROVIDER_CTP, new CtpMarketDataProducerFactory());

        PluginService pluginService = beansContainer.getBean(PluginService.class);
        if (pluginService!=null) {
            for(Plugin plugin : pluginService.search(Plugin.PROP_EXPOSED_INTERFACES + "=" + MarketDataProducerFactory.class.getName())) {
                Map<String, MarketDataProducerFactory> pluginProducerFactories = plugin.getBeansOfType(MarketDataProducerFactory.class);
                result.putAll(pluginProducerFactories);
            }
        }

        return result;
    }

}
