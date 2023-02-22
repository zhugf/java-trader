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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

import net.jctp.CThostFtdcDepthMarketDataField;
import trader.common.beans.BeansContainer;
import trader.common.exchangeable.Exchange;
import trader.common.exchangeable.Exchangeable;
import trader.common.util.ConversionUtil;
import trader.common.util.DateUtil;
import trader.common.util.NetUtil;
import trader.common.util.NetUtil.HttpMethod;
import trader.common.util.StringUtil;
import trader.service.ServiceConstants.ConnState;
import trader.service.md.MarketData;
import trader.service.md.spi.AbsMarketDataProducer;

/**
 * 采用多线程方式抓取WEB数据
 */
public class WebMarketDataProducer extends AbsMarketDataProducer<CThostFtdcDepthMarketDataField> {
    private final static Logger logger = LoggerFactory.getLogger(WebMarketDataProducer.class);

    public static final String API_SINA = "sina";
    public static final String API_TENCENT = "tencent";

    public final static Map<String, String> SINA_WEB_REFER = new HashMap();
    static{
        SINA_WEB_REFER.put("Referer","https://finance.sina.com.cn/");
    }
    private ExecutorService executorService;
    /**
     * 行情获取间隔, 单位毫秒
     */
    private long fetchInterval = 1*1000;
    private int itemsPerThread = 200;
    private AtomicInteger fetchThread = new AtomicInteger();
    private LocalDateTime lastUpdateTime = null;
    private String api=API_SINA;
    private Map<String, AtomicLong> instrumentTimestamps = new ConcurrentHashMap<>();

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
        String intervalStr = getConnectionProps().getProperty("fetchInterval", "2s");
        fetchInterval = ConversionUtil.str2seconds(intervalStr)*1000;
        itemsPerThread = ConversionUtil.toInt(getConnectionProps().getProperty("itemsPerThread", "150"));
        api = ConversionUtil.toString(getConnectionProps().getProperty("api", "sina"));
        changeStatus(ConnState.Connecting);
        try {
            NetUtil.readHttpAsText("http://hq.sinajs.cn/list=sh000300", HttpMethod.GET, null, StringUtil.GBK, SINA_WEB_REFER);
            changeStatus(ConnState.Connected);
            if ( StringUtil.equalsIgnoreCase(api, API_SINA)) {
                executorService.execute(()->{
                    sinaFetchThreadFunc();
                });
            } else if ( StringUtil.equalsIgnoreCase(api, API_TENCENT)) {
                executorService.execute(()->{
                    tencentFetchThreadFunc();
                });
            }
        }catch(Throwable t) {
            changeStatus(ConnState.ConnectFailed);
            logger.error("WEB行情连接失败: "+t, t);
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

    /**
     * 判断当前是否交易时间
     * @return
     */
    private boolean canFetch() {
        boolean result=false;
        LocalDateTime ldt = LocalDateTime.now();
        int hhmm = ldt.getHour()*100+ldt.getMinute();
        if ( hhmm>=915 && hhmm<=1131 ) {
            result = true;
        } else if ( hhmm>=1259 && hhmm<=1501 ) {
            result = true;
        }
        return result;
    }

    /**
     * 新浪API获取数据线程, 定时启动数据抓取线程实际干活
     */
    private void tencentFetchThreadFunc() {
        logger.info("腾讯行情线程启动");
        connectCount++;
        long fetchTime = 0;
        while(getState()==ConnState.Connected) {
            long timeToWait = fetchInterval-(System.currentTimeMillis()-fetchTime);
            if ( timeToWait>0 ) {
                try{
                    Thread.sleep(timeToWait);
                }catch(Throwable t) {}
            }
            fetchTime = System.currentTimeMillis();
            if ( !subscriptions.isEmpty() && canFetch() ) {
                long fetchTime0 = fetchTime;
                for(var items:Lists.partition(subscriptions, itemsPerThread)) {
                    fetchThread.incrementAndGet();
                    executorService.execute(()->{
                        try {
                            tencentFetchAndDispatch(items, fetchTime0);
                        } catch(Throwable t) {
                            logger.error("腾讯行情抓取失败: "+t, t);
                        } finally {
                            fetchThread.decrementAndGet();
                        }
                    });
                }
            }
        }
        logger.info("腾讯行情线程结束");
    }

    private void tencentFetchAndDispatch(List<String> items, long fetchTime) throws Exception
    {
        StringBuilder url = new StringBuilder(5120);
        url.append("http://qt.gtimg.cn/q=");

        boolean needsComma = false;
        for(String str:items) {
            if (needsComma) {
                url.append(",");
            }
            url.append(str);
            needsComma=true;
        }
        String text = NetUtil.readHttpAsText(url.toString(), HttpMethod.GET, null, StringUtil.GBK, null);
        if ( logger.isTraceEnabled() ) {
            logger.trace("腾讯行情数据:\n"+text);
        }
        int tickCount=0;
        List<WebMarketData> ticks = new ArrayList<>();
        for(String line:text.split("\n")) {
            CThostFtdcDepthMarketDataField field = tencent2field(line);
            if ( field!=null ) {
                tickCount++;
                Exchangeable e = str2security(field.InstrumentID);
                var tick = new WebMarketData(getId(), e, field);
                var tickTimeChanged = true;
                AtomicLong lastTimestamp = instrumentTimestamps.get(field.InstrumentID);
                if (null==lastTimestamp) {
                    lastTimestamp = new AtomicLong(tick.updateTimestamp);
                    instrumentTimestamps.put(field.InstrumentID, lastTimestamp);
                } else {
                    tickTimeChanged = tick.updateTimestamp>lastTimestamp.get();
                }
                if (tickTimeChanged) {
                    ticks.add(tick);
                    lastTimestamp.set(tick.updateTimestamp);
                }
            }
        }

        if ( !ticks.isEmpty() ) {
            for(int i=0;i<ticks.size();i++) {
                notifyData(ticks.get(i));
            }
        }
        if ( logger.isDebugEnabled() ) {
            long et= System.currentTimeMillis();
            logger.debug("腾讯行情抓取数据: "+items.size()+" 行情数: "+tickCount+" 有效: "+ticks.size()+", 耗时  "+(et-fetchTime)+" ms");
        }
    }


    /**
     * 新浪API获取数据线程, 定时启动数据抓取线程实际干活
     */
    private void sinaFetchThreadFunc() {
        logger.info("新浪行情线程启动");
        connectCount++;
        long fetchTime = 0;
        while(getState()==ConnState.Connected) {
            long timeToWait = fetchInterval-(System.currentTimeMillis()-fetchTime);
            if ( timeToWait>0 ) {
                try{
                    Thread.sleep(timeToWait);
                }catch(Throwable t) {}
            }
            fetchTime = System.currentTimeMillis();
            if ( !subscriptions.isEmpty() && canFetch() ) {
                long fetchTime0 = fetchTime;
                for(var items:Lists.partition(subscriptions, itemsPerThread)) {
                    fetchThread.incrementAndGet();
                    executorService.execute(()->{
                        try {
                            sinaFetchAndDispatch(items, fetchTime0);
                        } catch(Throwable t) {
                            logger.error("新浪行情抓取失败: "+t, t);
                        } finally {
                            fetchThread.decrementAndGet();
                        }
                    });
                }
            }
        }
        logger.info("新浪行情线程启动");
    }

    private void sinaFetchAndDispatch(List<String> items, long fetchTime) throws Exception
    {
        StringBuilder url = new StringBuilder(5120);
        url.append("http://hq.sinajs.cn/list=");

        boolean needsComma = false;
        for(String str:items) {
            if (needsComma) {
                url.append(",");
            }
            url.append(str);
            needsComma=true;
        }
        String text = NetUtil.readHttpAsText(url.toString(), HttpMethod.GET, null, StringUtil.GBK, SINA_WEB_REFER);
        if ( logger.isTraceEnabled() ) {
            logger.trace("新浪行情数据:\n"+text);
        }
        int tickCount = 0;
        List<WebMarketData> ticks = new ArrayList<>();
        for(String line:text.split("\n")) {
            CThostFtdcDepthMarketDataField field = sina2field(line);
            if ( field!=null ) {
                tickCount++;
                Exchangeable e = str2security(field.InstrumentID);
                var tick = new WebMarketData(getId(), e, field);
                var tickTimeChanged = true;
                AtomicLong lastTimestamp = instrumentTimestamps.get(field.InstrumentID);
                if (null==lastTimestamp) {
                    lastTimestamp = new AtomicLong(tick.updateTimestamp);
                    instrumentTimestamps.put(field.InstrumentID, lastTimestamp);
                } else {
                    tickTimeChanged = tick.updateTimestamp>lastTimestamp.get();
                }
                if (tickTimeChanged) {
                    ticks.add(tick);
                    lastTimestamp.set(tick.updateTimestamp);
                }
            }
        }
        if ( items.size()!=tickCount ) {
            logger.info("新浪行情数据获取丢失: "+items+"\n"+text);
        }

        if ( !ticks.isEmpty() ) {
            for(int i=0;i<ticks.size();i++) {
                notifyData(ticks.get(i));
            }
        }
        if ( logger.isDebugEnabled() ) {
            long et= System.currentTimeMillis();
            logger.debug("新浪行情抓取数据: "+items.size()+" 行情数: "+tickCount+" 有效: "+ticks.size()+", 耗时  "+(et-fetchTime)+" ms");
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

    /**
     * 转换SINA的股票行情为FIELD对象:
     * <BR>var hq_str_sh601398="工商银行,5.660,5.680,5.660,5.680,5.650,5.650,5.660,99667109,564217383.000,12196100,5.650,6381400,5.640,4424000,5.630,1940300,5.620,1157200,5.610,3847372,5.660,8377056,5.670,12320154,5.680,7226049,5.690,5151500,5.700,2019-07-05,14:19:34,00";
     */
    public static CThostFtdcDepthMarketDataField sina2field(String line) {
        CThostFtdcDepthMarketDataField result = null;
        int idIdx = line.indexOf("hq_str_");
        int q1 = line.indexOf('"');
        int q2 = line.lastIndexOf('"');
        if ( q1>0 && q2>0 && q1!=q2 ) {
            //符合
        } else {
            return null;
        }
        try {
            String exchange = line.substring(idIdx+7, idIdx+7+2);
            String items[] = line.substring(q1+1, q2).split(",");
            result = new CThostFtdcDepthMarketDataField();
            if (StringUtil.equals("sh", exchange)) {
                result.ExchangeID = "sse";
            }else if (StringUtil.equals("sz", exchange)) {
                result.ExchangeID = "szse";
            }
            result.InstrumentID = line.substring(idIdx+7, idIdx+7+2+6);
            result.ExchangeInstID = "";

            result.PreClosePrice = ConversionUtil.toDouble(items[2]);
            result.OpenPrice = ConversionUtil.toDouble(items[1]);
            result.LastPrice = ConversionUtil.toDouble(items[3]);
            result.HighestPrice = ConversionUtil.toDouble(items[4]);
            result.LowestPrice = ConversionUtil.toDouble(items[5]);
            result.OpenInterest = ConversionUtil.toLong(items[8]); //Volume超范围
            result.Volume = (int)result.OpenInterest;
            result.Turnover = ConversionUtil.toDouble(items[9]);

            result.AskPrice1 = ConversionUtil.toDouble(items[21]);
            result.AskVolume1 = ConversionUtil.toInt(items[20]);
            result.AskPrice2 = ConversionUtil.toDouble(items[23]);
            result.AskVolume2 = ConversionUtil.toInt(items[22]);
            result.AskPrice3 = ConversionUtil.toDouble(items[25]);
            result.AskVolume3 = ConversionUtil.toInt(items[24]);
            result.AskPrice4 = ConversionUtil.toDouble(items[27]);
            result.AskVolume4 = ConversionUtil.toInt(items[26]);
            result.AskPrice5 = ConversionUtil.toDouble(items[29]);
            result.AskVolume5 = ConversionUtil.toInt(items[28]);

            result.BidPrice1 = ConversionUtil.toDouble(items[11]);
            result.BidVolume1 = ConversionUtil.toInt(items[10]);
            result.BidPrice2 = ConversionUtil.toDouble(items[13]);
            result.BidVolume2 = ConversionUtil.toInt(items[12]);
            result.BidPrice3 = ConversionUtil.toDouble(items[15]);
            result.BidVolume3 = ConversionUtil.toInt(items[14]);
            result.BidPrice4 = ConversionUtil.toDouble(items[17]);
            result.BidVolume4 = ConversionUtil.toInt(items[16]);
            result.BidPrice5 = ConversionUtil.toDouble(items[19]);
            result.BidVolume5 = ConversionUtil.toInt(items[18]);

            //将交易日 2019-01-01 格式改为 20190101
            String day = items[30];
            try {
                day = DateUtil.date2str(DateUtil.str2localdate(day));
            }catch(Throwable t) {}
            result.ActionDay = day;
            result.TradingDay = day;
            result.UpdateTime = items[31];
            return result;
        }catch(Throwable t) {
            logger.error("新浪行情解析异常: "+line);
            return null;
        }
    }

    public static CThostFtdcDepthMarketDataField tencent2field(String line) {
        CThostFtdcDepthMarketDataField result = null;
        int idIdx = line.indexOf("v_");
        int q1 = line.indexOf('"');
        int q2 = line.lastIndexOf('"');
        if ( q1>0 && q2>0 && q1!=q2 ) {
            //符合
        } else {
            return null;
        }
        try {
            String exchange = line.substring(idIdx+2, idIdx+2+2);
            String items[] = line.substring(q1+1, q2).split("~");
            result = new CThostFtdcDepthMarketDataField();
            if (StringUtil.equals("sh", exchange)) {
                result.ExchangeID = "sse";
            }else if (StringUtil.equals("sz", exchange)) {
                result.ExchangeID = "szse";
            }
            result.InstrumentID = line.substring(idIdx+2, idIdx+2+2+6);
            result.ExchangeInstID = "";

            result.LastPrice = ConversionUtil.toDouble(items[3]);
            result.PreClosePrice = ConversionUtil.toDouble(items[4]);
            result.OpenPrice = ConversionUtil.toDouble(items[5]);
            result.HighestPrice = ConversionUtil.toDouble(items[33]);
            result.LowestPrice = ConversionUtil.toDouble(items[34]);
            result.OpenInterest = ConversionUtil.toLong(items[36])*100; //手
            result.Volume = (int)result.OpenInterest;
            result.Turnover = ConversionUtil.toDouble(items[37])*10000; //万元

            result.BidPrice1 = ConversionUtil.toDouble(items[9]);
            result.BidVolume1 = ConversionUtil.toInt(items[10])*100;
            result.BidPrice2 = ConversionUtil.toDouble(items[11]);
            result.BidVolume2 = ConversionUtil.toInt(items[12])*100;
            result.BidPrice3 = ConversionUtil.toDouble(items[13]);
            result.BidVolume3 = ConversionUtil.toInt(items[14])*100;
            result.BidPrice4 = ConversionUtil.toDouble(items[15]);
            result.BidVolume4 = ConversionUtil.toInt(items[16])*100;
            result.BidPrice5 = ConversionUtil.toDouble(items[17]);
            result.BidVolume5 = ConversionUtil.toInt(items[18])*100;

            result.AskPrice1 = ConversionUtil.toDouble(items[19]);
            result.AskVolume1 = ConversionUtil.toInt(items[20])*100;
            result.AskPrice2 = ConversionUtil.toDouble(items[21]);
            result.AskVolume2 = ConversionUtil.toInt(items[22])*100;
            result.AskPrice3 = ConversionUtil.toDouble(items[23]);
            result.AskVolume3 = ConversionUtil.toInt(items[24])*100;
            result.AskPrice4 = ConversionUtil.toDouble(items[25]);
            result.AskVolume4 = ConversionUtil.toInt(items[26])*100;
            result.AskPrice5 = ConversionUtil.toDouble(items[27]);
            result.AskVolume5 = ConversionUtil.toInt(items[28])*100;

            //将交易日 2019-01-01 格式改为 20190101
            String dayhhmmss = items[30];
            String day = dayhhmmss.substring(0, 8);
            try {
                day = DateUtil.date2str(DateUtil.str2localdate(day));
            }catch(Throwable t) {}
            result.ActionDay = day;
            result.TradingDay = day;
            result.UpdateTime = dayhhmmss.substring(8);

            return result;
        }catch(Throwable t) {
            logger.error("腾讯行情解析异常: "+line ,t);
            return null;
        }
    }
}
