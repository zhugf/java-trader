package trader.simulator;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import trader.common.beans.BeansContainer;
import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableData;
import trader.common.exchangeable.ExchangeableData.DataInfo;
import trader.common.util.CSVDataSet;
import trader.common.util.CSVMarshallHelper;
import trader.common.util.CSVUtil;
import trader.common.util.StringUtil;
import trader.common.util.TraderHomeUtil;
import trader.service.log.LogServiceImpl;
import trader.service.md.MarketData;
import trader.service.md.MarketDataListener;
import trader.service.md.MarketDataProducer;
import trader.service.md.MarketDataProducerFactory;
import trader.service.md.MarketDataService;
import trader.service.md.MarketDataServiceImpl;

/**
 * 模拟市场行情驱动服务
 */
public class SimMarketDataService implements MarketDataService, SimMarketTimeAware {
    private final static Logger logger = LoggerFactory.getLogger(SimMarketDataService.class);

    private static class SimMDInfo {
        List<MarketData> marketDatas = new ArrayList<>();
        int nextDataIndex = 0;

        /**
         * 寻找下一个行情数据
         */
        public MarketData seek(LocalDateTime lastTime, LocalDateTime actionTime) {
            MarketData result = null;
            if ( lastTime==null ) { //第一次, 寻找与市场时间相等或最后一个小于市场时间的行情切片
                for(int i=0;i<marketDatas.size();i++) {
                    MarketData md = marketDatas.get(i);
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
                for(int i=nextDataIndex;i<marketDatas.size();i++) {
                    MarketData md = marketDatas.get(i);
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

    private Map<String, MarketDataProducerFactory> producerFactories;
    protected List<MarketDataListener> genericListeners = new ArrayList<>();
    protected Map<Exchangeable, List<MarketDataListener>> listeners = new HashMap<>();
    protected Set<Exchangeable> subscriptions = new TreeSet<>();
    protected Map<Exchangeable, SimMDInfo> mdInfos = new HashMap<>();

    private LocalDateTime lastTime;

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
            result = mdInfo.marketDatas.get(mdInfo.nextDataIndex-1);
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
        beansContainer.getBean(SimMarketTimeService.class).addListener(this);
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
               mdInfo.marketDatas.add(marketData);
            }
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
                return factory.create(Collections.emptyMap());
            }
        }
        return null;
    }

    public static Map<String, MarketDataProducerFactory> discoverProducerFactories(){
        LogServiceImpl.setLogLevel("trader.service.plugin", "ERROR");
        SimBeansContainer beansContainer = new SimBeansContainer();
        return MarketDataServiceImpl.discoverProducerProviders(beansContainer);
    }

    @Override
    public Collection<Exchangeable> getPrimaryContracts() {
        return Collections.emptyList();
    }

}
