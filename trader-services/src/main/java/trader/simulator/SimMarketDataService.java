package trader.simulator;

import java.io.IOException;
import java.text.MessageFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import trader.common.beans.BeansContainer;
import trader.common.beans.ServiceState;
import trader.common.config.ConfigUtil;
import trader.common.exception.AppRuntimeException;
import trader.common.exchangeable.Exchange;
import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableData;
import trader.common.exchangeable.ExchangeableData.DataInfo;
import trader.common.exchangeable.ExchangeableTradingTimes;
import trader.common.exchangeable.Future;
import trader.common.util.CSVDataSet;
import trader.common.util.CSVMarshallHelper;
import trader.common.util.CSVUtil;
import trader.common.util.DateUtil;
import trader.common.util.StringUtil;
import trader.common.util.TraderHomeUtil;
import trader.service.ServiceErrorConstants;
import trader.service.log.LogServiceImpl;
import trader.service.md.MarketData;
import trader.service.md.MarketDataListener;
import trader.service.md.MarketDataProducer;
import trader.service.md.MarketDataProducerFactory;
import trader.service.md.MarketDataService;
import trader.service.md.MarketDataServiceImpl;
import trader.service.util.SimpleBeansContainer;

/**
 * 模拟市场行情驱动服务
 */
public class SimMarketDataService implements MarketDataService, SimMarketTimeAware {
    private final static Logger logger = LoggerFactory.getLogger(SimMarketDataService.class);

    private static class SimMDInfo {
        ExchangeableTradingTimes tradingTimes;
        List<MarketData> ticks = new ArrayList<>();
        int nextDataIndex = 0;


        /**
         * 寻找下一个行情数据
         */
        public MarketData seek(LocalDateTime lastTime, LocalDateTime actionTime) {
            MarketData result = null;
            if ( lastTime==null ) { //第一次, 寻找与市场时间相等或最后一个小于市场时间的行情切片
                for(int i=0;i<ticks.size();i++) {
                    MarketData md = ticks.get(i);
                    int actionTimeCompare= actionTime.compareTo(md.updateTime);
                    if ( actionTimeCompare>=0 ) { //actionTime >= marketDataTime
                        nextDataIndex = i+1;
                        result = md;
                        continue;
                    } else {
                        nextDataIndex = i;
                        break;
                    }
                }
            } else { //后续, 寻找lastTime<=updateTime&&updateTime<=actionTime
                for(int i=nextDataIndex;i<ticks.size();i++) {
                    MarketData md = ticks.get(i);
                    int actionTimeCompare= actionTime.compareTo(md.updateTime);
                    if ( actionTimeCompare>=0 ) { //actionTime >= marketDataTime
                        nextDataIndex = i+1;
                        result = md;
                        continue;
                    } else {
                        nextDataIndex = i;
                        break;
                    }
                }
            }
            return result;
        }

    }

    private BeansContainer beansContainer;
    private SimMarketTimeService mtService;
    private Map<String, MarketDataProducerFactory> producerFactories;
    protected List<MarketDataListener> genericListeners = new ArrayList<>();
    protected Map<Exchangeable, List<MarketDataListener>> listeners = new HashMap<>();
    protected Set<Exchangeable> subscriptions = new TreeSet<>();
    protected Map<Exchangeable, SimMDInfo> mdInfos = new HashMap<>();

    protected LocalDateTime lastTime;

    @Override
    public ServiceState getState() {
        return ServiceState.Ready;
    }

    @Override
    public Map<String, MarketDataProducerFactory> getProducerFactories() {
        return producerFactories;
    }

    @Override
    public Collection<MarketDataProducer> getProducers() {
        return Collections.emptyList();
    }

    @Override
    public MarketDataProducer getProducer(String producerId) {
        return null;
    }

    @Override
    public Collection<Exchangeable> getSubscriptions() {
        return new ArrayList<>(subscriptions);
    }

    @Override
    public MarketData getLastData(Exchangeable e) {
        MarketData result = null;
        SimMDInfo mdInfo = mdInfos.get(e);
        if ( mdInfo!=null && mdInfo.nextDataIndex>0 ) {
            result = mdInfo.ticks.get(mdInfo.nextDataIndex-1);
        }
        return result;
    }

    @Override
    public void addSubscriptions(List<Exchangeable> subscriptions) {
        this.subscriptions.addAll(subscriptions);
    }

    @Override
    public void addListener(MarketDataListener listener, Exchangeable... exchangeables) {
        if ( exchangeables==null || exchangeables.length==0 ) {
            genericListeners.add(listener);
        }else {
            for(Exchangeable exchangeable:exchangeables) {
                List<MarketDataListener> holder = listeners.get(exchangeable);
                if ( null==holder ) {
                    holder = new ArrayList<>();
                    listeners.put(exchangeable, holder);
                    subscriptions.add(exchangeable);
                }
                holder.add(listener);
            }
        }
    }

    /**
     * 从Repository 加载测试时间段的数据
     */
    @Override
    public void init(BeansContainer beansContainer) throws Exception {
        this.beansContainer = beansContainer;
        mtService = beansContainer.getBean(SimMarketTimeService.class);
        //Load subscriptions
        String text = StringUtil.trim(ConfigUtil.getString(MarketDataServiceImpl.ITEM_SUBSCRIPTIONS));
        for(String instrumentId:StringUtil.split(text, ",|;|\r|\n")) {
            Exchangeable instrument = null;
            if ( instrumentId.startsWith("$")) {
                instrument = getPrimaryInstrument(null, instrumentId.substring(1));
            }else {
                instrument = Exchangeable.fromString(instrumentId);
            }
            if ( instrument!=null ) {
                subscriptions.add(instrument);
            }
        }
        if ( mtService!=null ) {
            mtService.addListener(this);
        }
        producerFactories = discoverProducerFactories();
    }

    @Override
    public void destroy() {

    }

    @Override
    public void onTimeChanged(LocalDate tradingDay, LocalDateTime actionTime) {
        //通知行情数据
        if ( lastTime==null ) {
            //第一次调用, 需要加载数据
            loadMarketData(tradingDay);
        }
        for(Exchangeable e:subscriptions) {
            SimMDInfo mdInfo = mdInfos.get(e);
            if ( mdInfo==null ) {
                continue;
            }
            MarketData md = mdInfo.seek(lastTime, actionTime);
            if ( md==null ) {
                continue;
            }
            md.postProcess(mdInfo.tradingTimes);
            for(MarketDataListener listener:genericListeners) {
                listener.onMarketData(md);
            }
            List<MarketDataListener> eListeners = listeners.get(e);
            if ( eListeners!=null ) {
                for(MarketDataListener listener:eListeners) {
                    listener.onMarketData(md);
                }
            }
        }
        lastTime = actionTime;
    }

    private void loadMarketData(LocalDate tradingDay) {
        ExchangeableData data = TraderHomeUtil.getExchangeableData();
        for(Exchangeable e:subscriptions) {
            SimMDInfo mdInfo  =new SimMDInfo();
            mdInfo.tradingTimes = e.exchange().getTradingTimes(e, tradingDay);
            DataInfo tickInfo = ExchangeableData.TICK_CTP;
            String tickCsv = null;
            try{
                tickCsv = data.load(e, tickInfo, tradingDay);
            }catch(Throwable t) {
                logger.error("加载 "+e+" 交易日 "+tradingDay+" TICK行情数据失败", t);
                throw new RuntimeException(t);
            }

            CSVMarshallHelper csvMarshallHelper = createCSVMarshallHelper(tickInfo);
            MarketDataProducer mdProducer = createMarketDataProducer(tickInfo);

            CSVDataSet csvDataSet = CSVUtil.parse(tickCsv);
            while(csvDataSet.next()) {
                MarketData marketData = mdProducer.createMarketData(csvMarshallHelper.unmarshall(csvDataSet.getRow()), tradingDay);
                mdInfo.ticks.add(marketData);
            }
            postprocessTicks(mdInfo.ticks);
            mdInfos.put(e, mdInfo);
        }
    }

    private CSVMarshallHelper createCSVMarshallHelper(DataInfo tickInfo) {
        String provider = tickInfo.provider();
        if (!StringUtil.isEmpty(provider)) {
            MarketDataProducerFactory factory = producerFactories.get(provider);
            if ( factory!=null ) {
                return factory.createCSVMarshallHelper();
            }
        }
        return null;
    }

    private MarketDataProducer createMarketDataProducer(DataInfo tickInfo) {
        String provider = tickInfo.provider();
        if (!StringUtil.isEmpty(provider)) {
            MarketDataProducerFactory factory = producerFactories.get(provider);
            if ( factory!=null ) {
                return factory.create(beansContainer, Collections.emptyMap());
            }
        }
        return null;
    }

    public static Map<String, MarketDataProducerFactory> discoverProducerFactories(){
        LogServiceImpl.setLogLevel("trader.service.plugin", "ERROR");
        SimpleBeansContainer beansContainer = new SimpleBeansContainer();
        return MarketDataServiceImpl.discoverProducerProviders(beansContainer);
    }

    @Override
    public Collection<Exchangeable> getPrimaryInstruments() {
        return Collections.emptyList();
    }

    /**
     * 从dayStats数据中找到主力合约和次主力合约. 如果dayStats数据不存在, 直接报错
     */
    @Override
    public Exchangeable getPrimaryInstrument(Exchange exchange, String commodity) {
        if ( mtService!=null ) {
            LocalDate tradingDay = mtService.getTradingDay();
            return getPrimaryInstrument(exchange, commodity, tradingDay);
        }
        return null;
    }

    protected static final Map<String, String> cachedDayStats = new HashMap<>();

    public static Exchangeable getPrimaryInstrument(Exchange exchange, String commodity, LocalDate tradingDay) {
        int occurence=0;
        char cc = commodity.charAt(commodity.length()-1);
        if ( cc>='0' && cc<='9') {
            occurence = cc-'0';
            commodity = commodity.substring(0, commodity.length()-1);
        }
        if ( exchange==null ) {
            exchange = Future.detectExchange(commodity);
        }
        ExchangeableData edata = TraderHomeUtil.getExchangeableData();
        Future cf = new Future(exchange, commodity, commodity);
        TreeMap<Long, Exchangeable> instruments = new TreeMap<>();
        //Load daily stats data
        try {
            if ( edata.exists(cf, ExchangeableData.DAYSTATS, null)) {
                String key = cf.uniqueId()+"-"+tradingDay;
                String cachedData = cachedDayStats.get(key);
                if ( cachedData==null ) {
                    cachedData = edata.load(cf, ExchangeableData.DAYSTATS, null);
                    cachedDayStats.put(key, cachedData);
                }
                CSVDataSet csvDataSet = CSVUtil.parse(cachedData);
                while(csvDataSet.next()) {
                    String statTradingDay = csvDataSet.get(ExchangeableData.COLUMN_TRADINGDAY);
                    long openInt = csvDataSet.getLong(ExchangeableData.COLUMN_OPENINT);
                    Exchangeable instrument = Exchangeable.fromString(csvDataSet.get(ExchangeableData.COLUMN_INSTRUMENT_ID));
                    if ( DateUtil.str2localdate(statTradingDay).equals(tradingDay) && StringUtil.equalsIgnoreCase(instrument.commodity(), commodity) ) {
                        instruments.put(openInt, instrument);
                    }
                }
            }
        }catch(IOException ioe) {
            throw new AppRuntimeException(ioe, ServiceErrorConstants.ERR_DATA_LOAD_FAILED,
                    MessageFormat.format("{0} 加载 dayStats 文件失败: {1}", commodity, ioe) );
        }
        if ( instruments.isEmpty() ) {
            throw new AppRuntimeException(ServiceErrorConstants.ERR_DATA_LOAD_FAILED,
                    MessageFormat.format("{0} {1} 在 dayStats 中无数据", commodity, tradingDay) );
        }
        List<Exchangeable> instruments0 = new ArrayList<>(instruments.values());
        Collections.reverse(instruments0);
        Exchangeable result = null;
        int instrumentOccurence=0;
        for(Exchangeable e:instruments0) {
            instrumentOccurence++;
            if ( instrumentOccurence>=occurence) {
                result = e;
                break;
            }
        }
        return result;
    }

    /**
     * 对原始TICK数据进行清理
     */
    public static void postprocessTicks(List<MarketData> ticks) {
        long lastTimestamp=0;
        if ( ticks.isEmpty() ) {
            return;
        }
        ZoneId zoneId = ticks.get(0).instrumentId.exchange().getZoneId();

        for(int i=0;i<ticks.size();i++) {
            MarketData tick = ticks.get(i);
            if ( tick.updateTimestamp<=lastTimestamp ) {
                tick.updateTimestamp=lastTimestamp+200;
                tick.updateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(tick.updateTimestamp), zoneId).toLocalDateTime();
            }
            lastTimestamp = tick.updateTimestamp;
        }
    }

}
