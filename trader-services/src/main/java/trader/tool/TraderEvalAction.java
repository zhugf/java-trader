package trader.tool;

import java.io.File;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ScheduledExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import trader.common.beans.BeansContainer;
import trader.common.beans.Lifecycle;
import trader.common.exchangeable.Exchange;
import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableTradingTimes;
import trader.common.exchangeable.MarketDayUtil;
import trader.common.util.DateUtil;
import trader.common.util.FileUtil;
import trader.common.util.JsonUtil;
import trader.common.util.PriceUtil;
import trader.common.util.StringUtil;
import trader.common.util.StringUtil.KVPair;
import trader.common.util.TraderHomeUtil;
import trader.service.concurrent.OrderedExecutor;
import trader.service.md.MarketDataService;
import trader.service.plugin.PluginService;
import trader.service.plugin.PluginServiceImpl;
import trader.service.repository.BORepository;
import trader.service.repository.BORepositoryConstants.BOEntityType;
import trader.service.ta.BarServiceImpl;
import trader.service.trade.Account;
import trader.service.trade.MarketTimeService;
import trader.service.trade.Order;
import trader.service.trade.OrderImpl;
import trader.service.trade.TradeConstants.AccMoney;
import trader.service.trade.TradeConstants.OdrVolume;
import trader.service.trade.TradeService;
import trader.service.tradlet.PlaybookImpl;
import trader.service.tradlet.TradletGroup;
import trader.service.tradlet.TradletService;
import trader.service.util.CmdAction;
import trader.service.util.SimpleBeansContainer;
import trader.simulator.SimBORepository;
import trader.simulator.SimMarketDataService;
import trader.simulator.SimMarketTimeService;
import trader.simulator.SimOrderedExecutor;
import trader.simulator.SimScheduledExecutorService;
import trader.simulator.SimTradletService;
import trader.simulator.trade.SimTradeService;

/**
 * 回测
 */
public class TraderEvalAction implements CmdAction {
    private static final String TIME_MODE_TRADING = "trading";
    private static final String TIME_MODE_NATURAL = "natural";

    protected PrintWriter writer;
    protected LocalDate beginDate;
    protected LocalDate endDate;
    /**
     * 时间模式: 自然时间, 交易日时间
     */
    protected String timeMode = "trading";
    protected String statsFile = "";

    @Override
    public String getCommand() {
        return "eval";
    }

    @Override
    public void usage(PrintWriter writer) {
        writer.println("eval -Dtrader.configFile=TRADE_XML --beginDate=YYYYMMDD --endDate=YYYYMMDD --timeMode=natural|trading");
        writer.println("\t回测");
    }

    @Override
    public int execute(BeansContainer beansContainer, PrintWriter writer, List<KVPair> options) throws Exception
    {
        this.writer = writer;
        if ( !parseOptions(options)) {
            return 1;
        }
        initLogger();
        //首先确定开始交易日
        LocalDate tradingDay = beginDate;
        if ( !MarketDayUtil.isMarketDay(Exchange.SHFE, tradingDay)) {
            tradingDay = MarketDayUtil.nextMarketDay(Exchange.SHFE, tradingDay);
        }
        writer.println("回测时间: "+DateUtil.date2str(beginDate)+" - "+DateUtil.date2str(endDate) +", 共 "+MarketDayUtil.getMarketDays(null, beginDate, endDate).length+" 交易日 ");
        long bt=System.currentTimeMillis();
        SimpleBeansContainer globalBeans = createGlobalBeans();
        while(!tradingDay.isAfter(endDate)) {
            //模拟每日交易
            var dailyInstruments = tradeDaily(globalBeans, tradingDay);
            tradingDay = MarketDayUtil.nextMarketDay(dailyInstruments.get(0).exchange(), tradingDay);
        }
        //输出交易统计
        saveStats(globalBeans);
        long et=System.currentTimeMillis();
        writer.println("回测结束, 耗时: "+(et-bt)/1000+" s");
        return 0;
    }

    private boolean parseOptions(List<KVPair> options) {
        beginDate = null;
        for(KVPair kv:options) {
            if ( StringUtil.isEmpty(kv.v)) {
                continue;
            }
            switch(kv.k.toLowerCase()) {
            case "begindate":
                beginDate = DateUtil.str2localdate(kv.v);
                break;
            case "enddate":
                endDate = DateUtil.str2localdate(kv.v);
                break;
            case "timemode":
                timeMode = kv.v;
                break;
            case "statsfile":
                statsFile = kv.v;
            }
        }
        if ( endDate==null && beginDate==null ) {
            writer.println("需要提供过滤参数: beginDate/endDate");
            return false;
        }
        if (StringUtil.isEmpty(statsFile)) {
            writer.println("需要提供输出文件名: statsFile");
        }
        return true;
    }

    /**
     * 每日交易
     */
    private List<Exchangeable> tradeDaily(SimpleBeansContainer globalBeans, LocalDate tradingDay) throws Exception
    {
        List<Exchangeable> dailyInstruments  = new ArrayList<>();
        if ( StringUtil.equalsIgnoreCase(timeMode, TIME_MODE_TRADING)) { //交易日
            //创建当日环境
            SimpleBeansContainer dailyBeans = createDailyBeans(globalBeans, tradingDay, null);
            SimMarketDataService mdService = dailyBeans.getBean(SimMarketDataService.class);
            SimMarketTimeService mtService = dailyBeans.getBean(SimMarketTimeService.class);
            dailyInstruments = new ArrayList<>(mdService.getSubscriptions());
            //时间片段循环
            while(mtService.nextTimePiece());
            //销毁环境
            destroyDailyBeans(globalBeans, dailyBeans);
        } else { //自然日交易
            SimMarketDataService mdService0 = new SimMarketDataService();
            mdService0.init(globalBeans);
            dailyInstruments = new ArrayList<>(mdService0.getSubscriptions());
            List<LocalDateTime> dayTimes = Collections.emptyList();
            for(var instrument:mdService0.getSubscriptions()) {
                ExchangeableTradingTimes tradingTimes = instrument.exchange().getTradingTimes(instrument, tradingDay);
                List<LocalDateTime> dayTimes2 = new ArrayList<>();
                for(var marketTime:tradingTimes.getMarketTimes()) {
                    if ( marketTime.toLocalDate().equals(tradingDay) ) {
                        dayTimes2.add(marketTime);
                    }
                }
                if ( dayTimes.isEmpty() || dayTimes2.get(0).isBefore(dayTimes.get(0))) {
                    dayTimes = dayTimes2;
                }
            }

            //日盘
            {
                SimpleBeansContainer dailyBeans = createDailyBeans(globalBeans, tradingDay, dayTimes.toArray(new LocalDateTime[dayTimes.size()]));
                SimMarketTimeService mtService = dailyBeans.getBean(SimMarketTimeService.class);
                //时间片段循环
                while(mtService.nextTimePiece());
                //销毁环境
                destroyDailyBeans(globalBeans, dailyBeans);
            }

            //当日夜盘, 属于下一交易日
            var tradingDay2 = MarketDayUtil.nextMarketDay(dailyInstruments.get(0).exchange(), tradingDay);
            LinkedList<LocalDateTime> nightTimes = new LinkedList<>();
            for(var instrument:mdService0.getSubscriptions()) {
                ExchangeableTradingTimes tradingTimes = instrument.exchange().getTradingTimes(instrument, tradingDay2);
                LinkedList<LocalDateTime>  nightTimes2 = new LinkedList<>();
                for(var marketTime:tradingTimes.getMarketTimes()) {
                    //夜盘可能存在周六数据, 需要也包括进来
                    if ( !marketTime.toLocalDate().equals(tradingDay2) ) {
                        nightTimes2.add(marketTime);
                    }
                }
                if ( nightTimes.isEmpty() || nightTimes2.getLast().isAfter(nightTimes.getLast()) ){
                    nightTimes = nightTimes2;
                }
            }
            if (nightTimes.size()>0) {
                SimpleBeansContainer dailyBeans = createDailyBeans(globalBeans, tradingDay2, nightTimes.toArray(new LocalDateTime[nightTimes.size()]));
                SimMarketTimeService mtService = dailyBeans.getBean(SimMarketTimeService.class);
                while(mtService.nextTimePiece());
                destroyDailyBeans(globalBeans, dailyBeans);
            }
        }

        return dailyInstruments;
    }

    /**
     * 保存交易统计数据
     */
    private void saveStats(SimpleBeansContainer beansContainer) throws Exception
    {
        BORepository repository = beansContainer.getBean(BORepository.class);
        JsonObject json = new JsonObject();

        var orders = new JsonArray();
        for(var odr:OrderImpl.loadAll(repository, null, beginDate)) {
            orders.add(odr.toJson());
        }

        var playbooks = new JsonArray();
        for(var pb : PlaybookImpl.loadAll(repository, null, null, beginDate)) {
            playbooks.add(pb.toJson());
        }

        String jsonText = repository.load(BOEntityType.Default, "simTxn");
        JsonElement accountJson = JsonParser.parseString(jsonText);

        json.add("orders", orders);
        json.add("playbooks", playbooks);
        json.add("account", accountJson);
        FileUtil.save(new File(statsFile), JsonUtil.json2str(json, true));
    }

    /**
     * 创建一些跨越交易日的服务
     */
    private SimpleBeansContainer createGlobalBeans() throws Exception
    {
        SimpleBeansContainer globalBeans = new SimpleBeansContainer();
        //插件
        PluginServiceImpl pluginService = new PluginServiceImpl();
        pluginService.setBeansContainer(globalBeans);
        globalBeans.addBean(PluginService.class, pluginService);
        pluginService.init();
        //全局的临时存储
        SimBORepository repository = new SimBORepository();
        globalBeans.addBean(BORepository.class, repository);
        return globalBeans;
    }

    /**
     * 为某个交易日创建运行环境
     */
    private SimpleBeansContainer createDailyBeans(SimpleBeansContainer globalBeans, LocalDate tradingDay, LocalDateTime[] marketTimes)
        throws Exception
    {
        SimpleBeansContainer beansContainer = new SimpleBeansContainer(globalBeans);
        SimMarketTimeService mtService = new SimMarketTimeService();
        SimOrderedExecutor orderedExecutor = new SimOrderedExecutor();
        SimScheduledExecutorService scheduledExecutorService = new SimScheduledExecutorService();
        SimMarketDataService mdService = new SimMarketDataService();
        SimTradeService tradeService = new SimTradeService();
        BarServiceImpl barService = new BarServiceImpl();
        SimTradletService tradletService = new SimTradletService();

        beansContainer.addBean(MarketTimeService.class, mtService);
        beansContainer.addBean(OrderedExecutor.class, orderedExecutor);
        beansContainer.addBean(ScheduledExecutorService.class, scheduledExecutorService);
        beansContainer.addBean(MarketDataService.class, mdService);
        beansContainer.addBean(TradeService.class, tradeService);
        beansContainer.addBean(BarServiceImpl.class, barService);
        beansContainer.addBean(TradletService.class, tradletService);

        mtService.setTradingDay(tradingDay);
        mdService.init(beansContainer);
        //根据交易日自动选择最长交易时间
        if ( null==marketTimes ) {
            for(var instrument:mdService.getSubscriptions()) {
                ExchangeableTradingTimes tradingTimes = instrument.exchange().getTradingTimes(instrument, tradingDay);
                LocalDateTime [] marketTimes2 = tradingTimes.getMarketTimes();
                if ( null==marketTimes ) {
                    marketTimes = marketTimes2;
                } else {
                    if ( marketTimes2[0].compareTo(marketTimes[0])<0 ) {
                        marketTimes=marketTimes2;
                    }
                }
            }
        }
        mtService.setTimeRanges(tradingDay, marketTimes);
        scheduledExecutorService.init(beansContainer);
        barService.init(beansContainer);
        tradeService.init(beansContainer);
        tradletService.init(beansContainer);

        return beansContainer;
    }

    /**
     * 销毁单个交易日环境
     */
    private void destroyDailyBeans(SimpleBeansContainer globalBeans, SimpleBeansContainer beans) {
        for(Class beanClass:beans.getAllBeans().keySet()) {
            if ( globalBeans.getBean(beanClass)!=null ) {
                continue;
            }
            Object bean = beans.getBean(beanClass);
            if ( bean instanceof Lifecycle ) {
                ((Lifecycle)bean).destroy();
            }
        }
    }

    /**
     * 初始化logback
     */
    private void initLogger() throws Exception
    {
        String configName = System.getProperty(TraderHomeUtil.PROP_TRADER_CONFIG_NAME);
        File logbackConfig = new File(TraderHomeUtil.getDirectory(TraderHomeUtil.DIR_WORK), "logback.xml");
        FileUtil.save(logbackConfig, logback);
        System.setProperty("logback.configurationFile", logbackConfig.getAbsolutePath());

        Logger logger = LoggerFactory.getLogger(TraderEvalAction.class);
        logger.info("开始");
    }

    private static final String logback="""
<configuration>

    <appender name="fileAppender"
        class="ch.qos.logback.core.FileAppender">
        <file>${trader.home}/logs/${trader.configName}.log</file>
        <encoder>
            <pattern>%d [%thread] %-5level %logger{30} - %msg %n</pattern>
        </encoder>
    </appender>

    <appender name="ASYNC" class="ch.qos.logback.classic.AsyncAppender">
        <appender-ref ref="fileAppender" />
        <queueSize>1024</queueSize>
    </appender>

    <root level="INFO">
        <appender-ref ref="ASYNC" />
    </root>

    <logger name="org.springframework.web" level="INFO" />
    <logger name="trader" level="INFO" />

    <logger name="org.springframework.web.socket.client" level="OFF" />
    <logger name="org.springframework.web.socket.adapter.jetty" level="OFF" />
    <logger name="org.reflections" level="ERROR" />
</configuration>
""";
}
