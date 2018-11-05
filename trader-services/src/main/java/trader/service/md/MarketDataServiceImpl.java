package trader.service.md;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.annotation.PostConstruct;
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
import trader.common.exchangeable.Exchangeable;
import trader.common.util.ConversionUtil;
import trader.common.util.StringUtil;
import trader.service.ServiceConstants.ConnState;
import trader.service.md.MarketDataProducer.Type;
import trader.service.md.ctp.CtpMarketDataProducer;

/**
 * 行情数据的接收和聚合
 */
@Service
public class MarketDataServiceImpl implements MarketDataService {
    private final static Logger logger = LoggerFactory.getLogger(MarketDataServiceImpl.class);

    /**
     * 行情数据源定义
     */
    public static final String ITEM_PRODUCERS = "MarketDataService/producer[]";

    /**
     * 主动订阅的品种
     */
    public static final String ITEM_SUBSCRIPTIONS = "MarketDataService/subscriptions";

    /**
     * Producer连接超时设置: 15秒
     */
    public static final int PRODUCER_CONNECTION_TIMEOUT = 15*1000;

    @Autowired
    private ConfigService configService;

    @Autowired
    private BeansContainer beansContainer;

    @Autowired
    private ExecutorService executorService;

    @Autowired
    private ScheduledExecutorService scheduledExecutorService;

    private volatile boolean reloadInProgress = false;

    private ServiceState state = ServiceState.Unknown;

    private MarketDataSaver dataSaver;

    /**
     * 采用copy-on-write多线程访问方式，可以不使用锁
     */
    private Map<String, AbsMarketDataProducer> producers = new HashMap<>();

    /**
     * 采用copy-on-write方式访问的主动订阅的品种
     */
    private List<Exchangeable> subscriptions = new ArrayList<>();

    private List<MarketDataListener> genericListeners = new ArrayList<>();

    /**
     * 需要使用读写锁
     */
    private Map<Exchangeable, MarketDataListenerHolder> listeners = new HashMap<>();

    private ReadWriteLock listenerLock = new ReentrantReadWriteLock();

    private Map<Exchangeable, MarketData> lastDatas = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        state = ServiceState.Starting;
        reloadSubscriptions();
        configService.addListener(null, new String[] {ITEM_SUBSCRIPTIONS}, (source, path, newValue)->{
            reloadSubscriptionsAndSubscribe();
        });
        reloadProducers();
        dataSaver = new MarketDataSaver(this);
        dataSaver.init(beansContainer);
        scheduledExecutorService.scheduleAtFixedRate(()->{
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
    }

    @PreDestroy
    public void destroy() {
        state = ServiceState.Stopped;

        if ( null!=dataSaver ) {
            dataSaver.destory();
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
        return lastDatas.get(e);
    }

    @Override
    public void addSubscriptions(List<Exchangeable> subscriptions) {
        List<Exchangeable> newSubscriptions = new ArrayList<>();
        try {
            listenerLock.readLock().lock();
            for(Exchangeable e:subscriptions) {
                if ( listeners.containsKey(e) || this.subscriptions.contains(e) ) {
                    continue;
                }
                newSubscriptions.add(e);
                this.subscriptions.add(e);
            }
        }finally {
            listenerLock.readLock().unlock();
        }
        if ( !newSubscriptions.isEmpty() && state==ServiceState.Ready) {
            producersSubscribe(newSubscriptions);
        }
    }

    @Override
    public Collection<Exchangeable> getSubscriptions(){
        Set<Exchangeable> exchangeables = new HashSet<>();
        try {
            listenerLock.readLock().lock();
            exchangeables.addAll(listeners.keySet());
        }finally {
            listenerLock.readLock().unlock();
        }
        exchangeables.addAll(subscriptions);
        return exchangeables;
    }

    @Override
    public void addListener(MarketDataListener listener, Exchangeable... exchangeables) {
        List<Exchangeable> subscribes = new ArrayList<>();
        try {
            listenerLock.writeLock().lock();
            if ( exchangeables==null || exchangeables.length==0 || (exchangeables.length==1&&exchangeables[0]==null) ){
                genericListeners.add(listener);
            } else {
                for(Exchangeable exchangeable:exchangeables) {
                    MarketDataListenerHolder holder = listeners.get(exchangeable);
                    if ( null==holder ) {
                        holder = new MarketDataListenerHolder();
                        listeners.put(exchangeable, holder);
                        subscribes.add(exchangeable);
                    }
                    holder.addListener(listener);
                }
            }
        }finally {
            listenerLock.writeLock().unlock();
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
    void onProducerStateChanged(AbsMarketDataProducer producer, ConnState oldStatus) {
        if ( producer.getState()==ConnState.Connected ) {
            Collection<Exchangeable> exchangeables = getSubscriptions();
            if ( exchangeables.size()>0 ) {
                executorService.execute(()->{
                    producer.subscribe(exchangeables);
                });
            }
        }
    }

    void onProducerData(MarketData md) {
        dataSaver.onMarketData(md);
        lastDatas.put(md.instrumentId, md);

        MarketDataListenerHolder holder= listeners.get(md.instrumentId);
        if ( null!=holder && holder.checkTimestamp(md.updateTimestamp) ) {
            //notify listeners
            List<MarketDataListener> listeners = holder.getListeners();
            for(int i=0;i<listeners.size();i++) {
                try {
                    listeners.get(i).onMarketData(md);
                }catch(Throwable t) {
                    logger.error("Marketdata listener "+listeners.get(i)+" process failed: "+md,t);
                }
            }
        }

        for(int i=0;i<genericListeners.size();i++) {
            try{
                genericListeners.get(i).onMarketData(md);
            }catch(Throwable t) {
                logger.error("Marketdata listener "+genericListeners.get(i)+" process failed: "+md,t);
            }
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

    private List<Exchangeable> reloadSubscriptions() {
        String text = StringUtil.trim(ConfigUtil.getString(ITEM_SUBSCRIPTIONS));
        String[] instrumentIds = StringUtil.split(text, ",|;|\r|\n");
        List<Exchangeable> lastInstruments = this.subscriptions;
        List<Exchangeable> newInstruments = new ArrayList<>();
        List<Exchangeable> allSubscriptions = new ArrayList<>();

        for(String instrumentId:instrumentIds) {
            Exchangeable e = Exchangeable.fromString(instrumentId);
            allSubscriptions.add(e);
            if ( !lastInstruments.contains(e) ) {
                newInstruments.add(e);
            }
        }
        this.subscriptions = allSubscriptions;
        String message = "Total "+allSubscriptions.size()+" subscriptions loaded, "+newInstruments.size()+" added";
        if ( newInstruments.size()>0 ) {
            logger.info(message);
        }else {
            logger.debug(message);
        }
        return newInstruments;
    }

    /**
     * 重新加载并主动订阅
     */
    private void reloadSubscriptionsAndSubscribe() {
        List<Exchangeable> newInstruments = reloadSubscriptions();
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

    private AbsMarketDataProducer createMarketDataProducer(Map producerConfig) throws Exception
    {
        String id = (String)producerConfig.get("id");
        AbsMarketDataProducer result = null;
        Type type = ConversionUtil.toEnum(Type.class, producerConfig.get("type"));
        if ( null!=type ) {
            switch(type) {
            case ctp:
                result = new CtpMarketDataProducer(this, producerConfig);
                break;
            default:
            }
        }
        if ( null==result ) {
            throw new Exception("producer "+id+" type is null or unsupported: "+type);
        }
        return result;
    }

}
