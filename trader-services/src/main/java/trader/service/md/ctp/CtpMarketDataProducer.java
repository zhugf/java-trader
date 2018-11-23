package trader.service.md.ctp;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.jctp.*;
import trader.common.beans.Discoverable;
import trader.common.exchangeable.Exchange;
import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableType;
import trader.common.exchangeable.MarketDayUtil;
import trader.common.util.DateUtil;
import trader.common.util.EncryptionUtil;
import trader.common.util.StringUtil;
import trader.service.ServiceConstants.ConnState;
import trader.service.md.AbsMarketDataProducer;
import trader.service.md.MarketData;
import trader.service.md.MarketDataProducer;
import trader.service.md.MarketDataProducerFactory;
import trader.service.md.MarketDataServiceImpl;

@Discoverable(interfaceClass = MarketDataProducerFactory.class, purpose = MarketDataProducer.PROVIDER_CTP)
public class CtpMarketDataProducer extends AbsMarketDataProducer<CThostFtdcDepthMarketDataField> implements MdApiListener {
    private final static Logger logger = LoggerFactory.getLogger(CtpMarketDataProducer.class);

    private MdApi mdApi;

    private LocalDate actionDay;

    public CtpMarketDataProducer(MarketDataServiceImpl service, Map producerElemMap) {
        super(service, producerElemMap);
    }

    @Override
    public String getProvider() {
        return PROVIDER_CTP;
    }

    @Override
    public void connect() {
        actionDay = LocalDate.now();
        changeStatus(ConnState.Connecting);
        String url = connectionProps.getProperty("frontUrl");
        String brokerId = connectionProps.getProperty("brokerId");
        String username = connectionProps.getProperty("username");
        String password = connectionProps.getProperty("password");
        if (EncryptionUtil.isEncryptedData(username)) {
            username = new String(EncryptionUtil.symmetricDecrypt(username), StringUtil.UTF8);
        }
        if (EncryptionUtil.isEncryptedData(password)) {
            password = new String(EncryptionUtil.symmetricDecrypt(password), StringUtil.UTF8);
        }
        try{
            subscriptions = new ArrayList<>();
            mdApi = new MdApi();
            mdApi.setListener(this);
            mdApi.Connect(url, brokerId, username, password);
            logger.info(getId()+" connect to "+url);
        }catch(Throwable t) {
            if ( null!=mdApi ) {
                try{
                    mdApi.Close();
                }catch(Throwable t2) {}
            }
            mdApi = null;
            changeStatus(ConnState.ConnectFailed);
            logger.error(getId()+" connect to "+url+" failed",t);
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
        try {
            mdApi.SubscribeMarketData(instrumentIds.toArray(new String[instrumentIds.size()]));
        } catch (Throwable t) {
            logger.error(getId()+" subscribe failed with instrument ids : "+instrumentIds);
        }
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
        if ( logger.isInfoEnabled() ) {
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
        //根据CTP行情数据决定当天日期
        LocalDate actionDay = this.actionDay;
        int timeInt = DateUtil.time2int( pDepthMarketData.UpdateTime );
        if ( timeInt<=23000 ) {
            actionDay = actionDay.plusDays(1);
        }
        MarketData md = createMarketData(pDepthMarketData, actionDay);
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
    public MarketData createMarketData(CThostFtdcDepthMarketDataField ctpMarketData, LocalDate actionDay) {
        Exchangeable exchangeable = findOrCreate(ctpMarketData.ExchangeID, ctpMarketData.InstrumentID);
        if (actionDay==null) {
            actionDay = tradingDay2actionDay(ctpMarketData, exchangeable);
        }
        CtpMarketData md = new CtpMarketData(getId(), exchangeable, ctpMarketData, actionDay);
        return md;
    }

    private LocalDate tradingDay2actionDay(CThostFtdcDepthMarketDataField rawMarketData, Exchangeable exchangeable) {
        LocalDate result = null;
        int timeInt = DateUtil.time2int(rawMarketData.UpdateTime);
        if ( timeInt>=80000 && timeInt<=185000 ) {
            //日市--tradingDay==actionDay
            result = actionDay;
            if ( result==null ) {
                result = LocalDate.now();
            }
        } else {
            //夜市 tradingDay-1 = actionDay
            result = MarketDayUtil.prevMarketDay(exchangeable.exchange(), DateUtil.str2localdate(rawMarketData.TradingDay));
            //夜市的00:00-02:30, 夜市后半场
            if ( timeInt<30000 ) {
                result = result.plusDays(1);
            }
        }
        return result;
    }

}
