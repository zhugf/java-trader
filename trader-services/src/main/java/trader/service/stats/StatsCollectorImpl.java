package trader.service.stats;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import trader.common.util.IniFile;
import trader.common.util.ResourceUtil;
import trader.common.util.StringUtil;
import trader.common.util.SystemUtil;
import trader.service.ServiceConstants;

/**
 * 每分钟collect一次数据
 */
@Service
public class StatsCollectorImpl implements StatsCollector {

    private static final Logger logger = LoggerFactory.getLogger(StatsCollectorImpl.class);

    @Autowired
    private ApplicationContext appContext;

    private ScheduledExecutorService scheduledService;

    private Map<String, Properties> statsItemDefs;

    /**
     * Static items from addStatsItem/registerStatsItem
     */
    private Map<StatsItem, StatsItemCollectionEntry> statsItems = new ConcurrentHashMap<>();

    /**
     * Dynamic items from registerDyanmicStatsItems
     */
    private Map<StatsItem, StatsItemCollectionEntry> dynamicStatsItems = new ConcurrentHashMap<>();

    private List<StatsItemFactory> itemFactories = new ArrayList<>();

    private String thisNodeName;

    private String thisApplicationName;

    private StatsPublishEndpoint endpoint;

    private volatile long lastSampleMinutes;

    public StatsCollectorImpl()
    {
        try {
            statsItemDefs = new HashMap<>();
            statsItemDefs = loadStatsItemDefs();
        }catch(Exception e) {
            logger.error("load stats item definition file failed", e);
        }
        thisNodeName = SystemUtil.getHostName();
        thisApplicationName = System.getProperty(ServiceConstants.SYSPROP_APPLICATION_NAME);
    }

    public void setScheduledExecutorService(ScheduledExecutorService svc)
    {
        this.scheduledService = svc;
    }

    @PostConstruct
    public void init(){
        if ( scheduledService!=null ) {
            logger.info("Schedule stats collection task per second");
            scheduledService.scheduleAtFixedRate(()->{ collectAndSendPerSecond(); }, 1, 1, TimeUnit.SECONDS);
        }
        //Attach to local aggregation service automatically
        try {
            StatsAggregator aggService = appContext.getBean(StatsAggregator.class);
            if ( aggService!=null ){
                this.endpoint = (List<StatsItemPublishEvent> events)->{
                    aggService.aggregate(events);
                };
            }
        } catch(Throwable t) {}
    }

    @Override
    public void setEndpoint(StatsPublishEndpoint endpoint) {
        this.endpoint = endpoint;
    }

    @Override
    public void addStatsItemValue(StatsItem itemInfo, long itemValueToAdd) {
        if ( itemInfo.getType()!=StatsItemType.Cumulative ) {
            throw new RuntimeException("Stats item "+itemInfo.getKey()+" type expects cumulative to add value "+itemValueToAdd);
        }
        getItem(statsItems, itemInfo).addValue(itemValueToAdd);
    }

    @Override
    public void setStatsItemValue(StatsItem itemInfo, double instantItemValue) {
        getItem(statsItems, itemInfo).setValue(instantItemValue);
    }

    @Override
    public void registerStatsItem(StatsItem itemInfo, StatsItemValueGetter itemValueGetter) {
        getItem(statsItems, itemInfo).setValueGetter(itemValueGetter);
    }

    @Override
    public void registerDynamicStatsItems(StatsItemFactory itemFactory) {
        itemFactories.add(itemFactory);
    }

    @Override
    public List<StatsItemPublishEvent> instantSample(){
        return sampleAll(true);
    }

    /**
     * Collect and send events, invoked per minute from spring
     */
    @Scheduled(cron = "0 * * * * *")
    public void collectAndSend() {
        List<StatsItemPublishEvent> sampledData = sampleAll(false);
        if ( logger.isDebugEnabled() ) {
            logger.debug("Publish stats sample data: "+sampledData.size());
        }
        if( sampledData.size()>0 ) {
            try {
                publish(sampledData);
            } catch (Exception e2) {
                logger.error("Publish sampled events failed", e2);
            }
        }
    }

    /**
     * 每秒一次调用, 每分钟开始时执行采样和发送操作
     */
    private void collectAndSendPerSecond() {
        Instant now = Instant.now();
        long epochSeconds = now.getEpochSecond();
        long epochMinutes = epochSeconds/60;
        long secondInMinute = epochSeconds%60;
        if ( logger.isDebugEnabled()) {
            logger.debug("epochMinutes: "+epochMinutes+", secondInMInute: "+secondInMinute+", lastSampleMinute: "+lastSampleMinutes);
        }
        if ( secondInMinute!=0 ) {
            return;
        }
        if ( epochSeconds==lastSampleMinutes ) {
            return;
        }
        lastSampleMinutes = epochMinutes;
        collectAndSend();
    }

    /**
     * return new sampled data since last sample.
     * <BR>Note: this function should be invoked once per minute
     */
    private List<StatsItemPublishEvent> sampleAll(boolean instantSample) {
        if ( logger.isTraceEnabled()) {
            logger.trace("sampleAll ENTER");
        }
        List<StatsItemPublishEvent> result = new ArrayList<>();
        //Pick up updated sample items from fixed stats items
        long sampleTime = Instant.now().getEpochSecond();
        for( StatsItemCollectionEntry itemEntry:statsItems.values() ) {
            if ( itemEntry.isValueUpdated() ) {
                StatsItemPublishEvent event = instantSample?itemEntry.instantSample(sampleTime):itemEntry.sample(sampleTime);
                if ( logger.isDebugEnabled()) {
                    logger.debug("sample "+itemEntry.getItem()+" value "+event.getSampleValue());
                }
                result.add(event);
            }
        }
        //Sample all dynamic items
        for(StatsItemFactory itemFactory: itemFactories ){
            for(StatsItem dynamicItem:itemFactory.getStatsItems()){
                StatsItemCollectionEntry dynamicItemEntry = getItem(dynamicStatsItems, dynamicItem);
                StatsItemPublishEvent event = instantSample?dynamicItemEntry.instantSample(sampleTime):dynamicItemEntry.sample(sampleTime);
                if ( logger.isDebugEnabled()) {
                    logger.debug("sample "+dynamicItemEntry.getItem()+" value "+event.getSampleValue());
                }
                result.add(event);
            }
        }
        if ( logger.isTraceEnabled()) {
            logger.trace("sampleAll EXIT: "+result);
        }
        return result;
    }

    /**
     * Fill the node name of statsItem.
     * <BR>If no node name, will use this host name as the value
     */
    private StatsItemCollectionEntry getItem(Map<StatsItem, StatsItemCollectionEntry> items, StatsItem itemInfo)
    {
        if ( StringUtil.isEmpty(itemInfo.getNode()) ) {
            itemInfo.setNode(thisNodeName);
        }
        if ( StringUtil.isEmpty(itemInfo.getApplication())) {
            itemInfo.setApplication(thisApplicationName);
        }
        StatsItemCollectionEntry item = items.get(itemInfo);
        if ( item==null ) {
            item = new StatsItemCollectionEntry(itemInfo);
            fillItemInfo(itemInfo);
            items.put(itemInfo, item);
        }
        return item;
    }

    /**
     * Send out the sampled data via endpoint
     */
    private void publish(List<StatsItemPublishEvent> events) throws IOException
    {
        if (endpoint==null) {
            logger.warn("StatsPublishEndpoint is not configured");
            return;
        }
        endpoint.publish(events);
    }

    /**
     * 填充属性, 从INI文件
     */
    private void fillItemInfo(StatsItem itemInfo)
    {
        String key = itemInfo.getService()+"."+itemInfo.getItem();
        Properties props = statsItemDefs.get(key);
        if ( props!=null ) {
            String type = props.getProperty("type");
            if ( type!=null ) {
                itemInfo.setType(StatsItemType.valueOf(type));
            }
            String persistent = props.getProperty("persistent");
            if ( persistent!=null ) {
                itemInfo.setPersistent(Boolean.valueOf(persistent));
            }
            String cumulativeOnRestart = props.getProperty("cumulativeOnRestart");
            if ( cumulativeOnRestart!=null ) {
                itemInfo.setCumulativeOnRestart(Boolean.valueOf(cumulativeOnRestart));
            }
        }else {
            if ( logger.isDebugEnabled() ) {
                logger.debug("Stats item "+itemInfo+" has no definition");
            }
            itemInfo.setType(StatsItemType.Instant);
            if ( itemInfo.getItem().toLowerCase().startsWith("total")) {
                itemInfo.setType(StatsItemType.Cumulative);
                itemInfo.setCumulativeOnRestart(true);
            }else if ( itemInfo.getItem().toLowerCase().startsWith("curr")) {
                itemInfo.setType(StatsItemType.Instant);
            }
        }
    }

    /**
     * 加载stats Item 定义
     */
    private Map<String, Properties> loadStatsItemDefs() throws IOException
    {
        Map<String, Properties> result = new HashMap<>();
        ClassLoader[] classLoaders = new ClassLoader[]{ getClass().getClassLoader(), ClassLoader.getSystemClassLoader(), Thread.currentThread().getContextClassLoader()};
        List<URL> urls = ResourceUtil.loadResources("services/stats/statsItemDefs.ini", classLoaders);
        for(URL url:urls) {
            try(InputStream is=url.openStream();){
                IniFile iniFile = new IniFile(is);
                for(IniFile.Section s:iniFile.getAllSections()) {
                    result.put(s.getName(), s.getProperties());
                }
            }
        }
        return result;
    }

}
