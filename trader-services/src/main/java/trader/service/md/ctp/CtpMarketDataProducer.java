package trader.service.md.ctp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
import trader.common.exchangeable.MarketDayUtil;
import trader.common.util.DateUtil;
import trader.common.util.StringUtil;
import trader.service.ServiceConstants.ConnState;
import trader.service.md.MarketData;
import trader.service.md.MarketDataProducer;
import trader.service.md.MarketDataProducerFactory;
import trader.service.md.spi.AbsMarketDataProducer;
import trader.service.trade.MarketTimeService;
import trader.service.trade.ctp.CtpUtil;

@Discoverable(interfaceClass = MarketDataProducerFactory.class, purpose = MarketDataProducer.PROVIDER_CTP)
public class CtpMarketDataProducer extends AbsMarketDataProducer<CThostFtdcDepthMarketDataField> implements MdApiListener {
    private final static Logger logger = LoggerFactory.getLogger(CtpMarketDataProducer.class);

    private MdApi mdApi;

    private LocalDate tradingDay;

    private String tradingDayStr;

    /**
     * 每秒更新一次
     */
    private LocalDateTime actionTime;

    private String actionDayStr;

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
        final MarketTimeService mtService = beansContainer.getBean(MarketTimeService.class);

        if ( actionTime==null ) {
            actionTime = mtService.getMarketTime();
            actionDayStr = DateUtil.date2str(actionTime.toLocalDate());
            beansContainer.getBean(ScheduledExecutorService.class).scheduleAtFixedRate(()->{
                actionTime = mtService.getMarketTime();
                actionDayStr = DateUtil.date2str(actionTime.toLocalDate());
            }, 1, 1, TimeUnit.SECONDS);
        }

        tradingDay = mtService.getTradingDay();
        tradingDayStr = DateUtil.date2str(tradingDay);
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
    public void subscribe(Collection<Exchangeable> instruments) {
        List<String> instrumentIds = new ArrayList<>(instruments.size());
        for(Exchangeable e:instruments) {
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
        }, 5, TimeUnit.SECONDS);
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
        Exchangeable instrument = CtpUtil.ctp2instrument(pDepthMarketData.ExchangeID, pDepthMarketData.InstrumentID);
        adjustMarketData(pDepthMarketData, instrument);
        MarketData md = createMarketData(pDepthMarketData, instrument, tradingDay);
        notifyData(md);
    }

    @Override
    public MarketData createMarketData(CThostFtdcDepthMarketDataField ctpMarketData, LocalDate tradingDay) {
        Exchangeable instrument = CtpUtil.ctp2instrument(ctpMarketData.ExchangeID, ctpMarketData.InstrumentID);
        return createMarketData(ctpMarketData, instrument, tradingDay);
    }

    public MarketData createMarketData(CThostFtdcDepthMarketDataField ctpMarketData, Exchangeable instrument, LocalDate tradingDay) {
        return new CtpMarketData(getId(), instrument, ctpMarketData, tradingDay);
    }

    /**
     * 调整DCE/CZCE的TICK数据
     */
    private void adjustMarketData(CThostFtdcDepthMarketDataField tick, Exchangeable instrument)
    {
        if( actionTime==null ) {
            return;
        }

        //周五夜市DCE的ActionDay提前3天, CZCE的TradingDay晚了3天, SHFE正常
        //2015-01-30 21:03:00 DCE ActionDay 20150202, TraingDay 20150202
        //2015-01-30 21:03:00 CZCE ActionDay 20150130, TraingDay 20150130
        //2015-01-30 21:03:00 SHFE ActionDay 20150130, TraingDay 20150202

        //每天早上推送一条昨晚夜市收盘的价格, 但是ActionDay/TradingDay 都是当天白天日市数据
        //这时需要用lastActionDay处理
        boolean lastActionDay = false;

        Exchange exchange = instrument.exchange();
        if ( exchange==Exchange.DCE ) {
            tick.ActionDay = actionDayStr;
            int timeInt = DateUtil.time2int(tick.UpdateTime);
            if ( actionTime.getHour()<=9 && timeInt>= 150000 ) {
                lastActionDay = true;
            }
        }else if ( exchange==Exchange.CZCE ) {
            int timeInt = DateUtil.time2int(tick.UpdateTime);
            tick.TradingDay = tradingDayStr;
            //日市会将夜市的ClosePrice记录下来
            if ( actionTime.getHour()<=9 && timeInt>150000 ) {
                lastActionDay = true;
            }
        }else if ( exchange==Exchange.SHFE) {
            int timeInt = DateUtil.time2int(tick.UpdateTime);
            if ( actionTime.getHour()<=9 && timeInt>150000 ) {
                lastActionDay = true;
            }
        }

        if (lastActionDay) {
            LocalDate actionDay0 = MarketDayUtil.prevMarketDay(exchange, tradingDay);
            tick.ActionDay = DateUtil.date2str(actionDay0);
        }
    }

}
