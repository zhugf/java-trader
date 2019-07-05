package trader.service.md.ctp;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.jctp.CThostFtdcDepthMarketDataField;
import net.jctp.CThostFtdcForQuoteRspField;
import net.jctp.CThostFtdcRspInfoField;
import net.jctp.CThostFtdcRspUserLoginField;
import net.jctp.CThostFtdcSpecificInstrumentField;
import net.jctp.CThostFtdcUserLogoutField;
import net.jctp.MdApi;
import net.jctp.MdApiListener;
import trader.common.beans.BeansContainer;
import trader.common.beans.Discoverable;
import trader.common.exchangeable.Exchange;
import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableType;
import trader.common.util.DateUtil;
import trader.common.util.StringUtil;
import trader.service.ServiceConstants.ConnState;
import trader.service.md.MarketData;
import trader.service.md.MarketDataProducer;
import trader.service.md.MarketDataProducerFactory;
import trader.service.md.spi.AbsMarketDataProducer;
import trader.service.trade.MarketTimeService;

@Discoverable(interfaceClass = MarketDataProducerFactory.class, purpose = MarketDataProducer.PROVIDER_CTP)
public class CtpMarketDataProducer extends AbsMarketDataProducer<CThostFtdcDepthMarketDataField> implements MdApiListener {
    private final static Logger logger = LoggerFactory.getLogger(CtpMarketDataProducer.class);

    private MdApi mdApi;

    private LocalDate tradingDay;

    /**
     * 是否异步log订阅的合约
     */
    private volatile boolean asyncLogSubInstrumentIds;
    private List<String> subInstrumentIds;

    public CtpMarketDataProducer(BeansContainer beansContainer, Map producerElemMap) {
        super(beansContainer, producerElemMap);
    }

    @Override
    public String getProvider() {
        return PROVIDER_CTP;
    }

    @Override
    public void connect() {
        tradingDay = beansContainer.getBean(MarketTimeService.class).getTradingDay();
        changeStatus(ConnState.Connecting);
        String url = connectionProps.getProperty("frontUrl");
        String brokerId = connectionProps.getProperty("brokerId");
        String userId = connectionProps.getProperty("userId");
        if ( StringUtil.isEmpty(userId)) {
            userId = connectionProps.getProperty("username");
        }
        String password = connectionProps.getProperty("password");
        userId = decrypt(userId);
        password = decrypt(password);
        try{
            subscriptions = new ArrayList<>();
            mdApi = new MdApi();
            mdApi.setListener(this);
            mdApi.Connect(url, brokerId, userId, password);
            logger.info(getId()+" connect "+url+", MD API version: "+mdApi.GetApiVersion());
        }catch(Throwable t) {
            if ( null!=mdApi ) {
                try{
                    mdApi.Close();
                }catch(Throwable t2) {}
            }
            mdApi = null;
            changeStatus(ConnState.ConnectFailed);
            logger.error(getId()+" connect "+url+" failed: "+t.toString(),t);
        }
    }

    @Override
    protected void close0() {
        if ( null!=mdApi ) {
            mdApi.Close();
            mdApi = null;
        }
        changeStatus(ConnState.Disconnected);
    }

    @Override
    public void subscribe(Collection<Exchangeable> exchangeables) {
        List<String> instrumentIds = new ArrayList<>(exchangeables.size());
        for(Exchangeable e:exchangeables) {
            if ( canSubscribe(e) ) {
                instrumentIds.add(e.id());
            }
        }
        Collections.sort(instrumentIds);
        asyncLogSubInstrumentIds=true;
        subInstrumentIds = new ArrayList<>();
        try {
            mdApi.SubscribeMarketData(instrumentIds.toArray(new String[instrumentIds.size()]));
        } catch (Throwable t) {
            logger.error(getId()+" subscribe failed with instrument ids : "+instrumentIds);
            asyncLogSubInstrumentIds = false;
            subInstrumentIds = null;
        }
        ScheduledExecutorService scheduledExecutorService = beansContainer.getBean(ScheduledExecutorService.class);
        scheduledExecutorService.schedule(()->{
            List<String> instrumentIdsToLog = subInstrumentIds;
            asyncLogSubInstrumentIds = false;
            subInstrumentIds = null;
            logger.info(getId()+" confirm "+instrumentIds.size()+" instruments are subscribled : "+instrumentIdsToLog);
        }, 1, TimeUnit.SECONDS);
    }

    @Override
    public boolean canSubscribe(Exchangeable e) {
        if ( e.getType()==ExchangeableType.FUTURE ) {
            Exchange exchange = e.exchange();
            if ( exchange==Exchange.SHFE || exchange==Exchange.CZCE || exchange==Exchange.DCE || exchange==Exchange.CFFEX || exchange==Exchange.INE ) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void OnFrontConnected() {
        if ( logger.isInfoEnabled() ) {
            logger.info(getId()+" is connected");
        }
        connectCount++;
    }

    @Override
    public void OnFrontDisconnected(int arg0) {
        if ( logger.isInfoEnabled() ) {
            logger.info(getId()+" is disconnected");
        }
        if ( state!=ConnState.ConnectFailed ) {
            changeStatus(ConnState.Disconnected);
        }
    }

    @Override
    public void OnRspUserLogout(CThostFtdcUserLogoutField pUserLogout, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
        logger.info(getId()+" logout");
    }

    @Override
    public void OnRspUserLogin(CThostFtdcRspUserLoginField pRspUserLogin, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
        logger.info(getId()+" login "+pRspUserLogin+" rsp: "+pRspInfo);
        if ( pRspInfo.ErrorID==0 ) {
            changeStatus(ConnState.Connected);
            tradingDay = DateUtil.str2localdate(pRspUserLogin.TradingDay);
        }else {
            changeStatus(ConnState.ConnectFailed);
        }
    }

    @Override
    public void OnRspUnSubMarketData(CThostFtdcSpecificInstrumentField pSpecificInstrument, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
        String instrumentId = pSpecificInstrument.InstrumentID;
        if ( logger.isInfoEnabled() ) {
            logger.info(getId()+" unsubscribe: "+instrumentId);
        }
        subscriptions.remove(instrumentId);
    }

    @Override
    public void OnRspSubMarketData(CThostFtdcSpecificInstrumentField pSpecificInstrument, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
        String instrumentId = pSpecificInstrument.InstrumentID;
        if ( asyncLogSubInstrumentIds && subInstrumentIds!=null ) {
            subInstrumentIds.add(instrumentId);
        }else {
            logger.info(getId()+" subscribe: "+instrumentId);
        }
        if ( !subscriptions.contains(instrumentId)) {
            subscriptions.add(instrumentId);
        }
    }

    @Override
    public void OnRspError(CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
        if ( logger.isInfoEnabled() ) {
            logger.info(getId()+" got error response: "+pRspInfo);
        }
    }

    @Override
    public void OnHeartBeatWarning(int nTimeLapse) {
        if ( logger.isDebugEnabled() ) {
            logger.debug(getId()+" heart beat warning "+nTimeLapse);
        }
    }

    @Override
    public void OnRspSubForQuoteRsp(CThostFtdcSpecificInstrumentField pSpecificInstrument, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
        if ( logger.isInfoEnabled() ) {
            logger.info(getId()+" subscribe quote response: "+pSpecificInstrument);
        }
    }

    @Override
    public void OnRspUnSubForQuoteRsp(CThostFtdcSpecificInstrumentField pSpecificInstrument, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
        if ( logger.isInfoEnabled() ) {
            logger.info(getId()+" unsubscribe quote response: "+pSpecificInstrument);
        }
    }

    @Override
    public void OnRtnForQuoteRsp(CThostFtdcForQuoteRspField pForQuoteRsp) {
    }

    @Override
    public void OnRtnDepthMarketData(CThostFtdcDepthMarketDataField pDepthMarketData) {
        MarketData md = createMarketData(pDepthMarketData, tradingDay);
        notifyData(md);
    }

    private Map<String, Exchangeable> exchangeableMap = new HashMap<>();
    public Exchangeable findOrCreate(String exchangeId, String instrumentId)
    {
        Exchangeable r = exchangeableMap.get(instrumentId);
        if ( r==null ){
            r = Exchangeable.create(Exchange.getInstance(exchangeId), instrumentId);
            exchangeableMap.put(instrumentId, r);
        }
        return r;
    }

    @Override
    public MarketData createMarketData(CThostFtdcDepthMarketDataField ctpMarketData, LocalDate tradingDay) {
        Exchangeable exchangeable = findOrCreate(ctpMarketData.ExchangeID, ctpMarketData.InstrumentID);
        CtpMarketData md = new CtpMarketData(getId(), exchangeable, ctpMarketData, tradingDay);
        return md;
    }

}
