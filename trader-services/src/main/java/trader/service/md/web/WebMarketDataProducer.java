package trader.service.md.web;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.jctp.CThostFtdcDepthMarketDataField;
import trader.common.beans.BeansContainer;
import trader.common.exchangeable.Exchange;
import trader.common.exchangeable.Exchangeable;
import trader.common.util.ConversionUtil;
import trader.common.util.DateUtil;
import trader.common.util.NetUtil;
import trader.common.util.StringUtil;
import trader.service.ServiceConstants.ConnState;
import trader.service.md.MarketData;
import trader.service.md.spi.AbsMarketDataProducer;

public class WebMarketDataProducer extends AbsMarketDataProducer<CThostFtdcDepthMarketDataField> {
    private final static Logger logger = LoggerFactory.getLogger(WebMarketDataProducer.class);

    private ExecutorService executorService;
    /**
     * 行情获取间隔, 单位毫秒
     */
    private long fetchInterval = 10*1000;
    private LocalDateTime lastUpdateTime = null;

    public WebMarketDataProducer(BeansContainer beansContainer, Map configMap) {
        super(beansContainer, configMap);
        executorService = beansContainer.getBean(ExecutorService.class);
    }

    @Override
    public String getProvider() {
        return PROVIDER_WEB;
    }

    @Override
    public boolean canSubscribe(Exchangeable e) {
        if ( e.exchange()==Exchange.SSE || e.exchange() ==Exchange.SZSE ) {
            return true;
        }
        return false;
    }

    @Override
    public MarketData createMarketData(CThostFtdcDepthMarketDataField rawMarketData, LocalDate actionDay) {
        Exchangeable exchangeable = str2security(rawMarketData.InstrumentID);
        return new WebMarketData(PROVIDER_WEB, exchangeable, rawMarketData);
    }

    @Override
    protected void close0() {
        changeStatus(ConnState.Disconnected);
    }

    @Override
    public void connect() {
        String intervalStr = getConnectionProps().getProperty("fetchInterval", "10s");

        fetchInterval = ConversionUtil.str2seconds(intervalStr)*1000;
        changeStatus(ConnState.Connecting);
        try {
            NetUtil.readHttpAsText("http://hq.sinajs.cn/list=sh601398", StringUtil.GBK);
            changeStatus(ConnState.Connected);
            executorService.execute(()->{
                fetchLoopThreadFunc();
            });
        }catch(Throwable t) {
            changeStatus(ConnState.ConnectFailed);
            logger.error("Connect to SINA WEB quote service failed: "+t, t);
        }
    }

    @Override
    public void subscribe(Collection<Exchangeable> exchangeables) {
        Set<String> newSubs = new TreeSet<>();
        newSubs.addAll(subscriptions);
        for(Exchangeable e:exchangeables) {
            if ( canSubscribe(e)) {
                String str = security2str(e);
                if ( !StringUtil.isEmpty(str) ) {
                    newSubs.add(str);
                }
            }
        }
        this.subscriptions = new ArrayList<>(newSubs);
    }

    private void fetchLoopThreadFunc() {
        logger.info("SINA WEB quote fetch thread started");
        while(getState()==ConnState.Connected) {
            if ( subscriptions.isEmpty() ) {
                try{
                    Thread.sleep(1000);
                }catch(Throwable t) {}
                continue;
            }
            long t0 = System.currentTimeMillis();
            try {
                fetchAndDispatch();
            }catch(Throwable t) {
                logger.error("Fetch SINA WEB quote failed: "+t, t);
            }
            long timeToWait = fetchInterval-(System.currentTimeMillis()-t0);
            if ( timeToWait>0 ) {
                try{
                    Thread.sleep(timeToWait);
                }catch(Throwable t) {}
            }
        }
        logger.info("SINA WEB quote fetch thread stopped");
    }

    private void fetchAndDispatch() throws Exception
    {
        StringBuilder url = new StringBuilder(512);
        url.append("http://hq.sinajs.cn/list=");

        boolean needsComma = false;
        for(String str:subscriptions) {
            if (needsComma) {
                url.append(",");
            }
            url.append(str);
            needsComma=true;
        }
        String text = NetUtil.readHttpAsText(url.toString(), StringUtil.GBK);
        List<WebMarketData> ticks = new ArrayList<>();
        for(String line:StringUtil.text2lines(text, true, true)) {
            CThostFtdcDepthMarketDataField field = line2field(line);
            if ( field!=null ) {
                Exchangeable e = str2security(field.InstrumentID);
                ticks.add(new WebMarketData(getId(), e, field));
            }
        }

        if ( !ticks.isEmpty() ) {
            WebMarketData f0 = ticks.get(0);
            //如果这次TICK与上次的更新时间戳相同, 不发送
            if ( lastUpdateTime==null || !lastUpdateTime.isEqual(f0.updateTime)) {
                lastUpdateTime = f0.updateTime;
                for(int i=0;i<ticks.size();i++) {
                    listener.onMarketData(ticks.get(i));
                }
            }
        }
    }

    private Map<String, Exchangeable> exchangeableMap = new HashMap<>();
    /**
     * sh00001 to 000001.sse
     */
    private Exchangeable str2security(String instrumentId)
    {
        Exchangeable r = exchangeableMap.get(instrumentId);
        if ( r==null ){
            Exchange exchange = null;
            if ( instrumentId.startsWith("sh")) {
                exchange = Exchange.SSE;
                instrumentId = instrumentId.substring(2);
            }else if ( instrumentId.startsWith("sz")) {
                exchange = Exchange.SZSE;
                instrumentId = instrumentId.substring(2);
            }
            if ( exchange!=null ) {
                r = Exchangeable.create(exchange, instrumentId);
                exchangeableMap.put(instrumentId, r);
            }
        }
        return r;
    }

    private String security2str(Exchangeable e) {
        if ( e.exchange()==Exchange.SSE) {
            return "sh"+e.id();
        }else if ( e.exchange()==Exchange.SZSE) {
            return "sz"+e.id();
        }else {
            return null;
        }
    }

    public static final Pattern SINA_PATTERN = Pattern.compile("var hq_str_(?<InstrumentId>s[h|z]\\d{6})=\"(?<InstrumentName>[^,]+),(?<OpenPrice>\\d+(\\.\\d+)?),(?<PreClosePrice>\\d+(\\.\\d+)?),(?<LastPrice>\\d+(\\.\\d+)?),(?<HighestPrice>\\d+(\\.\\d+)?),(?<LowestPrice>\\d+(\\.\\d+)?),(?<BidPrice>\\d+(\\.\\d+)?),(?<AskPrice>\\d+(\\.\\d+)?),(?<Volume>\\d+),(?<Turnover>\\d+(\\.\\d+)?),(?<BidVolume1>\\d+),(?<BidPrice1>\\d+(\\.\\d+)?),(?<BidVolume2>\\d+),(?<BidPrice2>\\d+(\\.\\d+)?),(?<BidVolume3>\\d+),(?<BidPrice3>\\d+(\\.\\d+)?),(?<BidVolume4>\\d+),(?<BidPrice4>\\d+(\\.\\d+)?),(?<BidVolume5>\\d+),(?<BidPrice5>\\d+(\\.\\d+)?),(?<AskVolume1>\\d+),(?<AskPrice1>\\d+(\\.\\d+)?),(?<AskVolume2>\\d+),(?<AskPrice2>\\d+(\\.\\d+)?),(?<AskVolume3>\\d+),(?<AskPrice3>\\d+(\\.\\d+)?),(?<AskVolume4>\\d+),(?<AskPrice4>\\d+(\\.\\d+)?),(?<AskVolume5>\\d+),(?<AskPrice5>\\d+(\\.\\d+)?),(?<TradingDay>\\d{4}-\\d{2}-\\d{2}),(?<UpdateTime>\\d{2}:\\d{2}:\\d{2}),\\d{2}\";");

    /**
     * 转换SINA的股票行情为FIELD对象:
     * <BR>var hq_str_sh601398="工商银行,5.660,5.680,5.660,5.680,5.650,5.650,5.660,99667109,564217383.000,12196100,5.650,6381400,5.640,4424000,5.630,1940300,5.620,1157200,5.610,3847372,5.660,8377056,5.670,12320154,5.680,7226049,5.690,5151500,5.700,2019-07-05,14:19:34,00";
     */
    public static CThostFtdcDepthMarketDataField line2field(String line) {
        CThostFtdcDepthMarketDataField result = null;
        Matcher matcher = SINA_PATTERN.matcher(line);
        if ( matcher.matches()) {
            result = new CThostFtdcDepthMarketDataField();
            result.ExchangeID = "";
            result.ExchangeInstID = "";
            result.InstrumentID = matcher.group("InstrumentId");
            result.PreClosePrice = ConversionUtil.toDouble(matcher.group("PreClosePrice"));
            result.OpenPrice = ConversionUtil.toDouble(matcher.group("OpenPrice"));
            result.LastPrice = ConversionUtil.toDouble(matcher.group("LastPrice"));
            result.LastPrice = ConversionUtil.toDouble(matcher.group("LastPrice"));
            result.HighestPrice = ConversionUtil.toDouble(matcher.group("HighestPrice"));
            result.LowestPrice = ConversionUtil.toDouble(matcher.group("LowestPrice"));
            result.Volume = ConversionUtil.toInt(matcher.group("Volume"));
            result.Turnover = ConversionUtil.toDouble(matcher.group("Turnover"));

            result.AskPrice1 = ConversionUtil.toDouble(matcher.group("AskPrice1"));
            result.AskVolume1 = ConversionUtil.toInt(matcher.group("AskVolume1"));
            result.AskPrice2 = ConversionUtil.toDouble(matcher.group("AskPrice2"));
            result.AskVolume2 = ConversionUtil.toInt(matcher.group("AskVolume2"));
            result.AskPrice3 = ConversionUtil.toDouble(matcher.group("AskPrice3"));
            result.AskVolume3 = ConversionUtil.toInt(matcher.group("AskVolume3"));
            result.AskPrice4 = ConversionUtil.toDouble(matcher.group("AskPrice4"));
            result.AskVolume4 = ConversionUtil.toInt(matcher.group("AskVolume4"));
            result.AskPrice5 = ConversionUtil.toDouble(matcher.group("AskPrice5"));
            result.AskVolume5 = ConversionUtil.toInt(matcher.group("AskVolume5"));

            result.BidPrice1 = ConversionUtil.toDouble(matcher.group("BidPrice1"));
            result.BidVolume1 = ConversionUtil.toInt(matcher.group("BidVolume1"));
            result.BidPrice2 = ConversionUtil.toDouble(matcher.group("BidPrice2"));
            result.BidVolume2 = ConversionUtil.toInt(matcher.group("BidVolume2"));
            result.BidPrice3 = ConversionUtil.toDouble(matcher.group("BidPrice3"));
            result.BidVolume3 = ConversionUtil.toInt(matcher.group("BidVolume3"));
            result.BidPrice4 = ConversionUtil.toDouble(matcher.group("BidPrice4"));
            result.BidVolume4 = ConversionUtil.toInt(matcher.group("BidVolume4"));
            result.BidPrice5 = ConversionUtil.toDouble(matcher.group("BidPrice5"));
            result.BidVolume5 = ConversionUtil.toInt(matcher.group("BidVolume5"));

            //将交易日 2019-01-01 格式改为 20190101
            String day = matcher.group("TradingDay");;
            try {
                day = DateUtil.date2str(DateUtil.str2localdate(day));
            }catch(Throwable t) {}
            result.ActionDay = day;
            result.TradingDay = day;
            result.UpdateTime = matcher.group("UpdateTime");
        } else {
            logger.warn("Parse SINA WEB data failed: "+line);
        }
        return result;
    }

}
