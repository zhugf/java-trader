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
import java.util.concurrent.atomic.AtomicInteger;

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
    private long fetchInterval = 2*1000;
    private int itemsPerThread = 150;
    private AtomicInteger fetchThread = new AtomicInteger();
    private LocalDateTime lastUpdateTime = null;
    private String api=API_SINA;

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
        String intervalStr = getConnectionProps().getProperty("fetchInterval", "3s");
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
        logger.info("TENCENT WEB quote fetch thread started");
        long fetchTime = 0;
        while(getState()==ConnState.Connected) {
            long timeToWait = fetchInterval-(System.currentTimeMillis()-fetchTime);
            if ( timeToWait>0 ) {
                try{
                    Thread.sleep(timeToWait);
                }catch(Throwable t) {}
            }
            fetchTime = System.currentTimeMillis();
            if ( !subscriptions.isEmpty() && fetchThread.get()==0 && canFetch() ) {
                for(var items:Lists.partition(subscriptions, itemsPerThread)) {
                    fetchThread.incrementAndGet();
                    executorService.execute(()->{
                        try {
                            tencentFetchAndDispatch(items);
                        } catch(Throwable t) {
                            logger.error("Fetch TENCENT WEB quote failed: "+t, t);
                        } finally {
                            fetchThread.decrementAndGet();
                        }
                    });
                }
            }
        }
        logger.info("TENCENT WEB quote fetch thread stopped");
    }

    private void tencentFetchAndDispatch(List<String> items) throws Exception
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
        List<WebMarketData> ticks = new ArrayList<>();
        for(String line:text.split("\n")) {
            CThostFtdcDepthMarketDataField field = tencent2field(line);
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


    /**
     * 新浪API获取数据线程, 定时启动数据抓取线程实际干活
     */
    private void sinaFetchThreadFunc() {
        logger.info("SINA WEB quote fetch thread started");
        long fetchTime = 0;
        while(getState()==ConnState.Connected) {
            long timeToWait = fetchInterval-(System.currentTimeMillis()-fetchTime);
            if ( timeToWait>0 ) {
                try{
                    Thread.sleep(timeToWait);
                }catch(Throwable t) {}
            }
            fetchTime = System.currentTimeMillis();
            if ( !subscriptions.isEmpty() && fetchThread.get()==0 && canFetch() ) {
                for(var items:Lists.partition(subscriptions, itemsPerThread)) {
                    fetchThread.incrementAndGet();
                    executorService.execute(()->{
                        try {
                            sinaFetchAndDispatch(items);
                        } catch(Throwable t) {
                            logger.error("Fetch SINA WEB quote failed: "+t, t);
                        } finally {
                            fetchThread.decrementAndGet();
                        }
                    });
                }
            }
        }
        logger.info("SINA WEB quote fetch thread stopped");
    }

    private void sinaFetchAndDispatch(List<String> items) throws Exception
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
        List<WebMarketData> ticks = new ArrayList<>();
        for(String line:text.split("\n")) {
            CThostFtdcDepthMarketDataField field = sina2field(line);
            if ( field!=null ) {
                Exchangeable e = str2security(field.InstrumentID);
                ticks.add(new WebMarketData(getId(), e, field));
            }
        }
        if ( items.size()!=ticks.size() ) {
            logger.info("SINA QUOTE fetch data missed: "+items+"\n"+text);
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
            logger.error("SINA QUOTE parse failed: "+line);
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
            result.OpenInterest = ConversionUtil.toLong(items[6]);
            result.Volume = (int)result.OpenInterest;
            result.Turnover = ConversionUtil.toDouble(items[37])*10000; //万元

            result.BidPrice1 = ConversionUtil.toDouble(items[9]);
            result.BidVolume1 = ConversionUtil.toInt(items[10]);
            result.BidPrice2 = ConversionUtil.toDouble(items[11]);
            result.BidVolume2 = ConversionUtil.toInt(items[12]);
            result.BidPrice3 = ConversionUtil.toDouble(items[13]);
            result.BidVolume3 = ConversionUtil.toInt(items[14]);
            result.BidPrice4 = ConversionUtil.toDouble(items[15]);
            result.BidVolume4 = ConversionUtil.toInt(items[16]);
            result.BidPrice5 = ConversionUtil.toDouble(items[17]);
            result.BidVolume5 = ConversionUtil.toInt(items[18]);

            result.AskPrice1 = ConversionUtil.toDouble(items[19]);
            result.AskVolume1 = ConversionUtil.toInt(items[20]);
            result.AskPrice2 = ConversionUtil.toDouble(items[21]);
            result.AskVolume2 = ConversionUtil.toInt(items[22]);
            result.AskPrice3 = ConversionUtil.toDouble(items[23]);
            result.AskVolume3 = ConversionUtil.toInt(items[24]);
            result.AskPrice4 = ConversionUtil.toDouble(items[25]);
            result.AskVolume4 = ConversionUtil.toInt(items[26]);
            result.AskPrice5 = ConversionUtil.toDouble(items[27]);
            result.AskVolume5 = ConversionUtil.toInt(items[28]);

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
            logger.error("TENCENT QUOTE parse failed: "+line ,t);
            return null;
        }
    }
}
