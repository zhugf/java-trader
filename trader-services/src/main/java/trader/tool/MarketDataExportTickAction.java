package trader.tool;

import java.io.File;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import trader.common.beans.BeansContainer;
import trader.common.beans.Discoverable;
import trader.common.exchangeable.Exchange;
import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableData;
import trader.common.exchangeable.MarketDayUtil;
import trader.common.util.CSVWriter;
import trader.common.util.DateUtil;
import trader.common.util.FileUtil;
import trader.common.util.StringUtil;
import trader.common.util.StringUtil.KVPair;
import trader.common.util.TraderHomeUtil;
import trader.service.md.MarketData;
import trader.service.md.MarketDataService;
import trader.service.ta.BarSeriesLoader;
import trader.service.util.CmdAction;
import trader.service.util.SimpleBeansContainer;
import trader.simulator.SimMarketDataService;

@Discoverable(interfaceClass = CmdAction.class, purpose = "DataFeaturesExportTickAction")
public class MarketDataExportTickAction implements CmdAction {

    static class TaskInfo{
        protected Exchangeable instrument;
        protected LocalDate beginDate;
        protected LocalDate endDate;
        protected String outputFile;
        TreeMap<LocalDate, List<MarketData>> ticksByDay = new TreeMap<>();
    }
    protected PrintWriter writer;
    protected ExchangeableData data;

    @Override
    public String getCommand() {
        return "marketData.exportTick";
    }

    @Override
    public void usage(PrintWriter writer) {
        writer.println("marketData exportTick --batchFile=<BATCH_FILE> --instrument=<EXCHANGEABLE> --beginDate=<BEGIN_DATE> --endDate=<END_DATE> --outputFile=<OUTPUT_FILE>");
        writer.println("\t导出品种的 TICK 数据特征");
    }

    @Override
    public int execute(BeansContainer beansContainer, PrintWriter writer, List<KVPair> options) throws Exception {
        final List<TaskInfo> contexts = parseOptions(options);
        this.writer = writer;
        data = TraderHomeUtil.getExchangeableData();
        if ( contexts.size()==1 ) {
            taskExecute(contexts.get(0));
        }else {
            final AtomicInteger completeTasks = new AtomicInteger();
            int cpuCount = Runtime.getRuntime().availableProcessors();
            ThreadPoolExecutor executor = new ThreadPoolExecutor(cpuCount, cpuCount, 1, TimeUnit.MINUTES, new LinkedBlockingQueue<>());
            for(TaskInfo context:contexts) {
                executor.execute(()->{
                    taskExecute(context);
                    completeTasks.incrementAndGet();
                });
            }
            //Wait for all tasks to complete
            while(true) {
                Thread.sleep(500);
                if ( completeTasks.get()>=contexts.size() ) {
                    writer.println("All tasks completed");
                    break;
                }
            }
        }
        return 0;
    }

    protected List<TaskInfo> parseOptions(List<KVPair> options) throws Exception
    {
        List<TaskInfo> result = new ArrayList<>();
        TaskInfo context = new TaskInfo();
        boolean batch=false;
        for(KVPair kv:options) {
            if ( StringUtil.isEmpty(kv.v)) {
                continue;
            }
            switch(kv.k.toLowerCase()) {
            case "batchfile":
                for(String line:FileUtil.readLines(new File(kv.v))) {
                    List<KVPair> lineOptions = StringUtil.line2kvpairs(line);
                    result.addAll(parseOptions(lineOptions));
                }
                batch=true;
                break;
            case "instrument":
                context.instrument = Exchangeable.fromString(kv.v);
                break;
            case "begindate":
                context.beginDate = DateUtil.str2localdate(kv.v);
                break;
            case "enddate":
                context.endDate = DateUtil.str2localdate(kv.v);
                break;
            case "outputfile":
                context.outputFile = kv.v;
                break;
            }
        }
        if ( !batch) {
            if (context.beginDate==null) {
                context.beginDate = MarketDayUtil.lastMarketDay(Exchange.SHFE, false);
            }
            if (context.endDate==null) {
                context.endDate = MarketDayUtil.lastMarketDay(Exchange.SHFE, false);
            }
            if ( context.outputFile==null ) {
                context.outputFile = context.instrument+"-tick.csv";
            }
            result.add(context);
        }
        return result;
    }

    private void taskExecute(TaskInfo taskInfo) {
        //对每一个交易日, 加载-1,0,+1一共3天的tick数据计算.
        LocalDate currDay = taskInfo.beginDate;
        while(!currDay.isAfter(taskInfo.endDate)) {
            try{
                List<MarketData> ticks = loadTicksAround(taskInfo, currDay);
                if ( !ticks.isEmpty() ) {
                    buildTicksData(taskInfo, currDay, ticks);
                }
                writer.println(taskInfo.instrument+" "+DateUtil.date2str(currDay)+" processed");
                writer.flush();
            }catch(Throwable t) {
                writer.println(taskInfo.instrument+" "+DateUtil.date2str(currDay)+" load FAILED: "+t.toString());
                writer.flush();
            }
            currDay = MarketDayUtil.nextMarketDay(taskInfo.instrument.exchange(), currDay);

        }
    }

    private void buildTicksData(TaskInfo taskInfo, LocalDate day, List<MarketData> ticks) {

        CSVWriter csvWriter = new CSVWriter(
                "tradingDay"        //交易日
                ,"timestamp"        //时间带MS
                ,"price"            //最后价格
                ,"cumOpenInt"       //累计持仓
                ,"openInt"          //持仓变化
                ,"turnover"         //成交金额
                ,"qty"              //成交数
                ,"bid"              //买一价
                ,"ask"              //卖一价
                ,"bidQty"           //买一量
                ,"askQty"           //卖一量
                ,"midPrice"         //中间价
                ,"wpr"              //挂单量加权平均价
                ,"lnWpr"            //wpr 的对数
                ,"wprRet"           //最新一笔行情的对数收益率
                ,"high"             //当前 ask、前一个 ask、最新价的最高价（构造最小级别 K 线用）
                ,"low"              //当前 bid、前一个 bid、最新价的最低价（狗仔最小级别 K 线用）
                ,"nextBid"          //主动卖单的成交价（估算，非交易所提供），用于回测
                ,"nextAsk"          //主动买单的成交价（估算，非交易所提供），用于回测
                ,"min1024"          //1024跳滚动最低价
                ,"max1024"          //1024跳滚动最高价
                ,"min2048"
                ,"max2048"
                ,"min4096"
                ,"max4096"
                );

    }

    private List<MarketData> loadTicksAround(TaskInfo taskInfo, LocalDate day) throws Exception
    {
        LocalDate day0 = MarketDayUtil.prevMarketDay(taskInfo.instrument.exchange(), day);
        LocalDate day2 = MarketDayUtil.nextMarketDay(taskInfo.instrument.exchange(), day);

        List<MarketData> ticks0 = loadTicks(taskInfo, day0);
        List<MarketData> ticks = loadTicks(taskInfo, day);
        List<MarketData> ticks2 = loadTicks(taskInfo, day2);

        if ( ticks0.isEmpty() || ticks.isEmpty() || ticks2.isEmpty() ) {
            return Collections.emptyList();
        }

        List<MarketData> result = new ArrayList<>();

        return result;
    }

    private List<MarketData> loadTicks(TaskInfo taskInfo, LocalDate tradingDay) throws Exception
    {
        List<MarketData> ticks = taskInfo.ticksByDay.get(tradingDay);
        if ( null==ticks ){
            SimpleBeansContainer beansContainer = new SimpleBeansContainer();
            final SimMarketDataService mdService = new SimMarketDataService();
            mdService.init(beansContainer);
            beansContainer.addBean(MarketDataService.class, mdService);
            BarSeriesLoader loader= new BarSeriesLoader(beansContainer, data);
            ticks = loader.setInstrument(taskInfo.instrument).loadMarketDataTicks(tradingDay, ExchangeableData.TICK_CTP);

            taskInfo.ticksByDay.put(tradingDay, ticks);
            if ( taskInfo.ticksByDay.size()>=5 ) {
                taskInfo.ticksByDay.remove(taskInfo.ticksByDay.firstKey());
            }
        }
        return ticks;
    }

}
