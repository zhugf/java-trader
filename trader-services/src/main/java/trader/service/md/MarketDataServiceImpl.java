package trader.service.md;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import trader.common.beans.BeansContainer;
import trader.common.config.ConfigService;
import trader.common.config.ConfigUtil;
import trader.common.exchangeable.Exchangeable;
import trader.common.util.ConversionUtil;
import trader.service.md.MarketDataProducer.Status;
import trader.service.md.MarketDataProducer.Type;

/**
 * 行情数据的接收和聚合
 */
@Service
public class MarketDataServiceImpl implements MarketDataService {
    private final static Logger logger = LoggerFactory.getLogger(MarketDataServiceImpl.class);

    public static final String ITEM_PRODUCERS = "marketData/producers[]";
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
    private ScheduledThreadPoolExecutor scheduledThreadPoolExecutor;

    private volatile boolean reloadInProgress = false;

    /**
     * 采用copy-on-write多线程访问方式，可以不使用锁
     */
    private Map<String, AbsMarketDataProducer> producers = new HashMap<>();

    /**
     * 需要使用读写锁
     */
    private Map<Exchangeable, List<MarketDataListener> > listeners = new HashMap<>();

    private ReadWriteLock listenerLock = new ReentrantReadWriteLock();

    @PostConstruct
    public void init() {
        reloadProducers();
        scheduledThreadPoolExecutor.scheduleAtFixedRate(()->{
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

    }

    @Override
    public Collection<MarketDataProducer> getProducers() {
        var result = new LinkedList<MarketDataProducer>();
        result.addAll(producers.values());
        return result;
    }

    @Override
    public void addMarketDataListener(MarketDataListener listener, Exchangeable... exchangeables) {
        List<Exchangeable> subscribes = new ArrayList<>();
        try {
            listenerLock.writeLock().lock();
            for(Exchangeable exchangeable:exchangeables) {
                List<MarketDataListener> holder = listeners.get(exchangeable);
                if ( null==holder ) {
                    holder = new ArrayList<>();
                    listeners.put(exchangeable, holder);
                    subscribes.add(exchangeable);
                }
                holder.add(listener);
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
    void onProducerStatusChanged(AbsMarketDataProducer producer, Status oldStatus) {
        if ( producer.getStatus()==Status.Connected ) {
            List<Exchangeable> exchangeables = new ArrayList<>(listeners.size());
            try {
                listenerLock.readLock().lock();
                exchangeables.addAll(listeners.keySet());
            }finally {
                listenerLock.readLock().unlock();
            }
            if ( exchangeables.size()>0 ) {
                executorService.execute(()->{
                    producer.subscribe(exchangeables);
                });
            }
        }
    }

    /**
     * 为行情服务器订阅品种
     */
    private void producersSubscribe(List<Exchangeable> exchangeables) {
        List<String> connectedIds = new ArrayList<>();
        List<AbsMarketDataProducer> connectedProducers = new ArrayList<>();
        for(AbsMarketDataProducer producer:producers.values()) {
            if ( producer.getStatus()!=Status.Connected ) {
                continue;
            }
            connectedIds.add(producer.getId());
            connectedProducers.add(producer);
        }

        if (logger.isInfoEnabled() ) {
            logger.info("Subscribe exchangeables "+exchangeables+" to producers: "+connectedIds);
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
            if ( p.getStatus()==Status.Disconnected ) {
                p.asyncConnect();
            }
        }
        //断开连接超时的Producer
        for(AbsMarketDataProducer p:producers.values()) {
            if ( p.getStatus()==Status.Connecting && (System.currentTimeMillis()-p.getStatusTime())>PRODUCER_CONNECTION_TIMEOUT) {
                p.close();
            }
        }
    }

    /**
     * 重新加载配置, 检查配置变化
     */
    private void reloadProducers() {
        long t0 = System.currentTimeMillis();
        Map<String, AbsMarketDataProducer> currProducers = this.producers;
        Map<String, AbsMarketDataProducer> newProducers = new HashMap<>();
        List<AbsMarketDataProducer> createdProducers = new ArrayList<>();
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
        logger.info("Total "+producers.size()+" producers loaded from "+producerConfigs.size()+" config items in "+(t1-t0)+" ms, added: "+newProducerIds+", deleted: "+delProducerIds);
        for(AbsMarketDataProducer p:createdProducers) {
            p.asyncConnect();
        }
    }

    private AbsMarketDataProducer createMarketDataProducer(Map producerConfig) throws Exception
    {
        AbsMarketDataProducer result = null;
        Type type = ConversionUtil.toEnum(Type.class, producerConfig.get("type"));
        switch(type) {
        case ctp:
            result = new CtpMarketDataProducer(this, producerConfig);
            break;
        default:
            throw new Exception("Unsupported market data producer type: "+type);
        }
        return result;
    }

}
