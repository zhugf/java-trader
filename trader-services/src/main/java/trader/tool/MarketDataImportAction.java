package trader.tool;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.ta4j.core.Bar;

import com.google.common.io.Files;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.jctp.CThostFtdcDepthMarketDataField;
import trader.common.beans.BeansContainer;
import trader.common.exchangeable.Exchange;
import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableData;
import trader.common.exchangeable.ExchangeableData.DataInfo;
import trader.common.exchangeable.ExchangeableTradingTimes;
import trader.common.exchangeable.Future;
import trader.common.exchangeable.MarketDayUtil;
import trader.common.exchangeable.MarketTimeStage;
import trader.common.util.CSVDataSet;
import trader.common.util.CSVMarshallHelper;
import trader.common.util.CSVUtil;
import trader.common.util.CSVWriter;
import trader.common.util.ConversionUtil;
import trader.common.util.DateUtil;
import trader.common.util.FileUtil;
import trader.common.util.PriceUtil;
import trader.common.util.StringUtil;
import trader.common.util.StringUtil.KVPair;
import trader.common.util.TraderHomeUtil;
import trader.common.util.csv.CtpCSVMarshallHelper;
import trader.service.md.MarketData;
import trader.service.md.MarketDataProducer;
import trader.service.md.MarketDataProducerFactory;
import trader.service.md.ctp.CtpMarketData;
import trader.service.md.web.WebMarketData;
import trader.service.ta.BarSeriesLoader;
import trader.service.ta.FutureBarImpl;
import trader.service.util.CmdAction;
import trader.simulator.SimMarketDataService;

/**
 * 行情数据的归档命令.
 * <BR>行情数据的临时保存的目录结构: TraderHome/marketData/20181010/mdProducerId/shfe.ru1901.csv
 */
public class MarketDataImportAction implements CmdAction {
    private static final List<String> ctpProviders = Arrays.asList("ctp", "web");

    /**
     * 用于特别处理CZCE这种同一秒内的UpdateTime毫秒数相同的情况
     */
    private static class CtpTicksPostProcessor{
        long lastVolume=0;
        long lastTimestamp;

        public boolean checkTick(MarketData tick) {
            boolean result = false;
            long volume = tick.volume;
            if ( volume>lastVolume) {
                result = true;
                lastVolume = volume;
                //如果 timestamp 相同, 每次累加500ms
                if ( tick.updateTimestamp<=lastTimestamp ) {
                    tick.updateTimestamp = lastTimestamp+500;
                    tick.updateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(tick.updateTimestamp), tick.instrument.exchange().getZoneId()).toLocalDateTime();
                }
                lastTimestamp = tick.updateTimestamp;
            }
            return result;
        }

    }

    /**
     * 代表一个CTP格式的文件
     */
    private static class CtpMarketDataInfo implements Comparable<CtpMarketDataInfo>{
        final LocalDate tradingDay;
        final Exchangeable exchangeable;
        final File tickFile;
        final String producerType;
        List<MarketData> ticks = new ArrayList<>(50000);
        /**
         * tick数量, 去除交易时间段之外的tick, 去除重复的tick
         */
        int tickCount;
        /**
         * 保存tick数量
         */
        int savedTicks;

        public CtpMarketDataInfo(LocalDate tradingDay, File file, String producerType) {
            this.tradingDay = tradingDay;
            this.tickFile = file;
            this.producerType = producerType;
            this.exchangeable = Exchangeable.fromString( FileUtil.getFileMainName(file));
        }

        public List<MarketData> load() throws IOException
        {
            CSVMarshallHelper csvMarshallHelper = createCSVMarshallHelper(producerType);
            MarketDataProducer mdProducer = createMarketDataProducer(producerType);

            ExchangeableTradingTimes tradingTimes = null;
            CSVDataSet csvDataSet = CSVUtil.parse(FileUtil.read(tickFile));
            CtpTicksPostProcessor ticksPostProcessor = new CtpTicksPostProcessor();
            while(csvDataSet.next()) {
                MarketData md = mdProducer.createMarketData(csvMarshallHelper.unmarshall(csvDataSet.getRow()), tradingDay);
                ticksPostProcessor.checkTick(md);
                Exchangeable e = md.instrument;
                if ( tradingTimes==null ) {
                    tradingTimes = e.exchange().getTradingTimes(e, tradingDay);
                }
                if ( tradingTimes==null ) {
                    continue;
                }
                ticks.add(md);
                tickCount++; //只计算正式开市的数据
            }

            return ticks;
        }

        public void clear() {
            ticks.clear();
        }

        @Override
        public int compareTo(CtpMarketDataInfo o) {
            return tickCount-o.tickCount;
        }

        private CSVMarshallHelper createCSVMarshallHelper(String producerType) {
            Map<String, MarketDataProducerFactory> producerFactories = SimMarketDataService.discoverProducerFactories();
            MarketDataProducerFactory factory = producerFactories.get(producerType);
            if ( factory!=null ) {
                return factory.createCSVMarshallHelper();
            }
            return null;
        }

        private MarketDataProducer createMarketDataProducer(String producerType) {
            Map<String, MarketDataProducerFactory> producerFactories = SimMarketDataService.discoverProducerFactories();
            MarketDataProducerFactory factory = producerFactories.get(producerType);
            if ( factory!=null ) {
                return factory.create(null, Collections.emptyMap());
            }
            return null;
        }

    }

    static class MdshareData{
        File file;
        Exchangeable instrument;
        LocalDate tradingDay;
        List<CThostFtdcDepthMarketDataField> ctpTicks = new ArrayList<>();
        List<MarketData> mds = new ArrayList<>();
        int ticksTotal;
        int ticksSaved;
    }

    private PrintWriter writer;
    private ExchangeableData data;
    private Map<String, MarketDataProducerFactory> producerFactories;
    private String producer="ctp";
    private List<String> instrumentFilters = new ArrayList<>();
    private String dataDir;
    private boolean moveToTrash;
    private boolean merge=true;
    private ThreadPoolExecutor executorService;

    public MarketDataImportAction() {
        executorService = new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors(), Runtime.getRuntime().availableProcessors(), 5, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
    }

    @Override
    public String getCommand() {
        return "marketData.import";
    }

    @Override
    public void usage(PrintWriter writer) {
        writer.println("marketData import [--producer=ctp|jinshuyuan|sqlite|mdshare] [--datadir=DATA_DIR] [--instruments=e1,e2,e3] [--move=trash|none] [--merge=true]");
        writer.println("\t导入行情数据");
    }

    @Override
    public int execute(BeansContainer beansContainer, PrintWriter writer, List<KVPair> options) throws Exception
    {
        this.writer = writer;
        data = TraderHomeUtil.getExchangeableData();
        producerFactories = SimMarketDataService.discoverProducerFactories();
        parseOptions(options);
        if ( StringUtil.equals(producer, "jinshuyuan")) {
            importFromJinshuyuanDir();
        } else if ( StringUtil.equals(producer, "sqlite")) {
            importSqlites();
        } else if ( StringUtil.equals(producer, "mdshare")) {
            importMdshare();
        } else if ( StringUtil.equals(producer, "ctp")) {
            importFromCtpDir();
        } else {
            throw new Exception("未知的producer: "+producer);
        }
        return 0;
    }

    /**
     * SQLITE 数据文件或目录
     */
    private void importSqlites() throws Exception
    {
        File mdDir = new File(dataDir);
        writer.println("从目录导入: "+mdDir.getAbsolutePath());writer.flush();
        LinkedList<File> files = new LinkedList<>();
        files.add(mdDir);
        while(!files.isEmpty()) {
            File file = files.poll();
            if ( file.isDirectory() ) {
                File[] files0 = file.listFiles();
                if ( files0!=null ) {
                    List<File> files2 = new ArrayList<>(Arrays.asList(files0));
                    Collections.sort(files2);
                    files.addAll(files2);
                }
                continue;
            }
            String fname = file.getName();
            if ( fname.endsWith("db") && fname.length()==11 && fname.startsWith("20")) {
                importSqlite(file);
            }
        }
    }

    private void importSqlite(File sqliteFile) throws Exception
    {
        writer.print(sqliteFile.getName()+" : "); writer.flush();
        String url = "jdbc:sqlite:"+sqliteFile.getAbsolutePath();
        try(Connection conn = DriverManager.getConnection(url);){
            LocalDate tradingDay = DateUtil.str2localdate( sqliteFile.getName().substring(0, 8) );
            List<String> tableNames = new ArrayList<>();
            try(ResultSet tableRs = conn.getMetaData().getTables(null, null, null, new String[] {"TABLE"});){
                while(tableRs.next()) {
                    tableNames.add( tableRs.getString("TABLE_NAME") );
                }
            }
            Collections.sort(tableNames);
            for(String tableName:tableNames) {
                //CFFEX_IF1904
                String nameParts[] = StringUtil.split(tableName, "_");
                Exchangeable instrument = Exchangeable.fromString(nameParts[1]);
                if ( data.exists(instrument, ExchangeableData.TICK_CTP, tradingDay) ) {
                    continue;
                }
                List<CThostFtdcDepthMarketDataField> ctpTicks = table2ticks(conn, tradingDay, tableName);

                CtpCSVMarshallHelper ctpCsvHelper = new CtpCSVMarshallHelper();
                CSVWriter<CThostFtdcDepthMarketDataField> ctpCsvWrite = new CSVWriter<>(ctpCsvHelper);
                for(CThostFtdcDepthMarketDataField ctpTick:ctpTicks) {
                    ctpCsvWrite.next();
                    ctpCsvWrite.marshall(ctpTick);
                }
                data.save(instrument, ExchangeableData.TICK_CTP, tradingDay, ctpCsvWrite.toString());
                writer.print("."); writer.flush();
            }
        }
        writer.println();writer.flush();
    }

    private List<CThostFtdcDepthMarketDataField> table2ticks(Connection conn, LocalDate tradingDay, String tableName) throws Exception
    {
        List<CThostFtdcDepthMarketDataField> result = new ArrayList<>();
        try(Statement stmt=conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT * FROM "+tableName);){
            while(rs.next()) {
                CThostFtdcDepthMarketDataField field = new CThostFtdcDepthMarketDataField();
                field.InstrumentID = rs.getString("instrument");
                field.TradingDay = DateUtil.date2str(tradingDay);
                field.ActionDay = rs.getString("date");
                field.UpdateTime = rs.getString("time");
                field.UpdateMillisec = rs.getInt("millisec");
                field.UpperLimitPrice = rs.getDouble("upper_limit_price");
                field.LowerLimitPrice = rs.getDouble("lower_limit_price");
                field.OpenPrice = rs.getDouble("open");
                field.HighestPrice = rs.getDouble("high");
                field.LowestPrice = rs.getDouble("low");
                field.LastPrice = rs.getDouble("last_price");
                field.Volume = rs.getInt("volume");
                field.Turnover = rs.getDouble("turnover");
                field.OpenInterest = rs.getDouble("open_int");

                field.AskPrice1 = rs.getDouble("ask_price1");
                field.AskVolume1 = rs.getInt("ask_vol1");
                field.AskPrice2 = rs.getDouble("ask_price2");
                field.AskVolume2 = rs.getInt("ask_vol2");
                field.AskPrice3 = rs.getDouble("ask_price3");
                field.AskVolume3 = rs.getInt("ask_vol3");
                field.AskPrice4 = rs.getDouble("ask_price4");
                field.AskVolume4 = rs.getInt("ask_vol4");
                field.AskPrice5 = rs.getDouble("ask_price5");
                field.AskVolume5 = rs.getInt("ask_vol5");

                field.BidPrice1 = rs.getDouble("bid_price1");
                field.BidVolume1 = rs.getInt("bid_vol1");
                field.BidPrice2 = rs.getDouble("bid_price2");
                field.BidVolume2 = rs.getInt("bid_vol2");
                field.BidPrice3 = rs.getDouble("bid_price3");
                field.BidVolume3 = rs.getInt("bid_vol3");
                field.BidPrice4 = rs.getDouble("bid_price4");
                field.BidVolume4 = rs.getInt("bid_vol4");
                field.BidPrice5 = rs.getDouble("bid_price5");
                field.BidVolume5 = rs.getInt("bid_vol5");

                field.SettlementPrice = rs.getDouble("settle_price");
                field.PreSettlementPrice = rs.getDouble("pre_settle_price");

                result.add(field);
            }
        }
        /*instrument
        date
        time
        millisec
        upper_limit_price
        lower_limit_price
        open
        high
        low
        last_price
        volume
        turnover
        open_int
        ask_price1
        ask_vol1
        ask_price2
        ask_vol2
        ask_price3
        ask_vol3
        ask_price4
        ask_vol4
        ask_price5
        ask_vol5
        ask_price6
        ask_vol6
        ask_price7
        ask_vol7
        ask_price8
        ask_vol8
        ask_price9
        ask_vol9
        ask_price10
        ask_vol10
        bid_price1
        bid_vol1
        bid_price2
        bid_vol2
        bid_price3
        bid_vol3
        bid_price4
        bid_vol4
        bid_price5
        bid_vol5
        bid_price6
        bid_vol6
        bid_price7
        bid_vol7
        bid_price8
        bid_vol8
        bid_price9
        bid_vol9
        bid_price10
        bid_vol10
        settle_price
        pre_settle_price*/

        return result;
    }

    private void importMdshare() throws Exception
    {
        File mdDir = new File(dataDir);
        writer.println("从目录导入: "+mdDir.getAbsolutePath());writer.flush();
        LinkedList<File> files = new LinkedList<>();
        files.add(mdDir);
        while(!files.isEmpty()) {
            File file = files.poll();
            if ( file.isDirectory() && DateUtil.str2localdate(file.getName())!=null ) {
                importMdShareDir(file);
                continue;
            }
            if ( file.isDirectory() ) {
                File[] childs = file.listFiles();
                if ( childs!=null ) {
                    List<File> files0 = new ArrayList<>(Arrays.asList(childs));
                    Collections.sort(files0);
                    files.addAll(files0);
                }
                continue;
            }
            if ( !file.getName().toLowerCase().endsWith(".csv") || file.getName().indexOf("主力")>=0 || file.getName().indexOf("连续")>=0 ) {
                continue;
            }
            LocalDate tradingDay = DateUtil.str2localdate(file.getParentFile().getName());
            Exchangeable instrument = null;
            try{
                instrument = Exchangeable.fromString(FileUtil.getFileMainName(file));
            }catch(Exception e) {}
            if ( tradingDay==null || instrument==null || !acceptInstrument(instrument) ) {
                continue;
            }
            writer.print(file.getAbsolutePath()+" : ");
            writer.flush();
            Exchangeable ctpFuture = null;
            if ( instrument.exchange()==Exchange.SHFE ) {
                ctpFuture = importMdshareFile(instrument, tradingDay, file);
            } else {
                ctpFuture = importMdshareFile(instrument, tradingDay, file);
            }
            writer.println(ctpFuture+" "+tradingDay);
        }
    }

    /**
     * 按目录并行导入
     */
    private void importMdShareDir(File dir) throws Exception
    {
        writer.println();
        writer.println("并行目录导入: "+dir.getAbsolutePath());writer.flush();
        List<java.util.concurrent.Future<MdshareData>> futures = new ArrayList<>();
        for(File file: dir.listFiles()) {
            if ( !file.getName().endsWith(".csv")) {
                continue;
            }
            LocalDate tradingDay = DateUtil.str2localdate(file.getParentFile().getName());
            Exchangeable instrument = null;
            try{
                instrument = Exchangeable.fromString(FileUtil.getFileMainName(file));
            }catch(Exception e) {}
            if ( tradingDay==null || instrument==null || !acceptInstrument(instrument) ) {
                continue;
            }
            Exchangeable instrument2 = instrument;
            futures.add( executorService.submit(()->{
                try {
                    if ( instrument2.exchange()==Exchange.SHFE ) {
                        return loadMdshareFile(instrument2, tradingDay, file);
                    } else {
                        return loadMdshareFile(instrument2, tradingDay, file);
                    }
                }catch(Throwable t) {
                    t.printStackTrace(writer);
                }
                return null;
            }));
        }
        for(java.util.concurrent.Future<MdshareData> f:futures) {
            MdshareData data = f.get();
            if ( data!=null ) {
                saveMdShareData(data);
            }
        }
    }

    private void saveMdShareData(MdshareData mdshare) throws Exception
    {
        DataInfo dataInfo = ExchangeableData.TICK_CTP;
        if ( mdshare.ctpTicks.size()==0 ) {
            return;
        }
        CtpCSVMarshallHelper ctpCsvHelper = new CtpCSVMarshallHelper();
        CSVMarshallHelper csvMarshallHelper = createCSVMarshallHelper(MarketDataProducer.PROVIDER_CTP);
        MarketDataProducer mdProducer = createMarketDataProducer(MarketDataProducer.PROVIDER_CTP);
        CSVWriter<CThostFtdcDepthMarketDataField> ctpCsvWrite = new CSVWriter<>(ctpCsvHelper);
        int ticksBeforeSave = 0;
        String existsData = null;
        if ( data.exists(mdshare.instrument, dataInfo, mdshare.tradingDay) && merge ) {
            List<MarketData> ticks0 = new ArrayList<>(50000);
            existsData = data.load(mdshare.instrument, dataInfo, mdshare.tradingDay);
            CSVDataSet csvDataSet = CSVUtil.parse(existsData);
            while(csvDataSet.next()) {
                MarketData tick = mdProducer.createMarketData(csvMarshallHelper.unmarshall(csvDataSet.getRow()), mdshare.tradingDay);
                ticks0.add(tick);
            }
            ticksBeforeSave = ticks0.size();
            List<List<MarketData>> allTicks = new ArrayList<>();
            allTicks.add(mdshare.mds);
            allTicks.add(ticks0);
            mdshare.mds = mergeAllTicks(mdshare.instrument, allTicks);
        }
        mdshare.ticksTotal = mdshare.mds.size();
        mdshare.ticksSaved = mdshare.ticksTotal-ticksBeforeSave;
        for(MarketData tick:mdshare.mds) {
            CtpMarketData tick0 = (CtpMarketData)tick;
            ctpCsvWrite.next().marshall(tick0.field);
        }
        String mergedData = ctpCsvWrite.toString();
        if ( mdshare.ticksSaved>0 || !StringUtil.equals(mergedData, existsData) ) {
            data.save(mdshare.instrument, ExchangeableData.TICK_CTP, mdshare.tradingDay, mergedData);
            saveDayBars(data, mdshare.instrument, mdshare.tradingDay, mdshare.mds);
            writer.print(" "+mdshare.instrument+" "+mdshare.ticksSaved+"/"+mdshare.ticksTotal); writer.flush();
        }
    }

    private MdshareData loadMdshareFile(Exchangeable instrument, LocalDate tradingDay, File file) throws Exception
    {
        MdshareData result = new MdshareData();
        result.file = file;
        result.instrument = instrument;
        result.tradingDay = tradingDay;
        ExchangeableTradingTimes tradingTimes = instrument .exchange().getTradingTimes(instrument, tradingDay);
        CtpCSVMarshallHelper ctpCsvHelper = new CtpCSVMarshallHelper();
        CSVDataSet ds = CSVUtil.parse(new InputStreamReader(new FileInputStream(file), StringUtil.GBK), ',', false);
        while(ds.next()) {
            String row[] = new String[] {
                    DateUtil.date2str(tradingDay) //交易日
                    ,ds.get(1) //合约代码
                    ,"" //交易所代码
                    ,"" //合约在交易所的代码
                    ,ds.get(6) //最新价
                    ,ds.get(23) //上次结算价
                    ,ds.get(21) //昨收盘
                    ,ds.get(22) //昨持仓量
                    ,ds.get(10) //今开盘
                    ,ds.get(8) //最高价
                    ,ds.get(9) //最低价
                    ,ds.get(7) //数量
                    ,ds.get(20) //成交金额
                    ,ds.get(19) //持仓量
                    ,ds.get(11) //今收盘
                    ,"" //本次结算价
                    ,ds.get(17) //涨停板价
                    ,ds.get(18) //跌停板价
                    ,"" //昨虚实度
                    ,"" //今虚实度
                    ,ds.get(4) //最后修改时间
                    ,ds.get(5) //最后修改毫秒

                    ,ds.get(15) //申买价一
                    ,ds.get(16) //申买量一
                    ,ds.get(13) //申卖价一
                    ,ds.get(14) //申卖量一

                    ,"" //ds.get("申买价二")
                    ,"" //ds.get("申买量二")
                    ,"" //ds.get("申卖价二")
                    ,"" //ds.get("申卖量二")

                    ,"" //ds.get("申买价三")
                    ,"" //ds.get("申买量三")
                    ,"" //ds.get("申卖价三")
                    ,"" //ds.get("申卖量三")

                    ,"" //ds.get("申买价四")
                    ,"" //ds.get("申买量四")
                    ,"" //ds.get("申卖价四")
                    ,"" //ds.get("申卖量四")

                    ,"" //ds.get("申买价五")
                    ,"" //ds.get("申买量五")
                    ,"" //ds.get("申卖价五")
                    ,"" //ds.get("申卖量五")

                    ,ds.get(12) //当日均价
                    ,ds.get(0).substring(0, 8) //"业务日期")
            };
            CThostFtdcDepthMarketDataField ctpData = ctpCsvHelper.unmarshall(row);
            if ( !ctpData.TradingDay.equals(DateUtil.date2str(tradingDay)) ) {
                throw new Exception("tradingDay not matches : "+Arrays.asList(ds.getRow()));
            }
            Exchangeable ctpFuture = Exchangeable.fromString(ctpData.InstrumentID);
            if ( !ctpFuture.equals(instrument)) {
                throw new Exception("Instrument id not matches "+Arrays.asList(ds.getRow()));
            }
            LocalDate ctpTradingDay = DateUtil.str2localdate(ctpData.TradingDay);
            CtpMarketData md = new CtpMarketData(MarketDataProducer.PROVIDER_CTP, ctpFuture, ctpData, ctpTradingDay);
            if ( tradingTimes!=null && tradingTimes.getTimeStage(md.updateTime)==MarketTimeStage.MarketOpen ) {
                result.ctpTicks.add(ctpData);
                result.mds.add(md);
            }
        }
        return result;
    }

    private Exchangeable importMdshareFile(Exchangeable instrument, LocalDate tradingDay, File file) throws Exception
    {
        MdshareData data = loadMdshareFile(instrument, tradingDay, file);
        saveMdShareData(data);
        return data.instrument;
    }

    /**
     * 金数源 目录
     */
    private void importFromJinshuyuanDir() throws Exception
    {
        executorService = new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors(), Runtime.getRuntime().availableProcessors(), 5, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        File mdDir = new File(dataDir);
        writer.println("从目录导入: "+mdDir.getAbsolutePath());writer.flush();
        List<java.util.concurrent.Future> futures = new ArrayList<>();
        LinkedList<File> files = new LinkedList<>();
        files.add(mdDir);
        while(!files.isEmpty()) {
            File file = files.poll();
            if ( file.isDirectory() ) {
                File[] childs = file.listFiles();
                if ( childs!=null ) {
                    List<File> files0 = new ArrayList<>(Arrays.asList(childs));
                    Collections.sort(files0);
                    files.addAll(files0);
                }
                continue;
            }
            if ( !file.getName().toLowerCase().endsWith(".csv") || file.getName().indexOf("主力")>=0 || file.getName().indexOf("连续")>=0 ) {
                continue;
            }
            java.util.concurrent.Future f = executorService.submit(()->{
                try {
                    jinshuyuanImportFile(file);
                }catch(Throwable e) {
                    writer.println("导入文件 "+file+" 失败: "+e.toString());
                }
            });
            futures.add(f);
        }
        for(java.util.concurrent.Future f:futures) {
            f.get();
        }
    }

    private void jinshuyuanImportFile(File file) throws IOException
    {
        CtpCSVMarshallHelper ctpCsvHelper = new CtpCSVMarshallHelper();
        CSVWriter<CThostFtdcDepthMarketDataField> ctpCsvWrite = new CSVWriter<>(ctpCsvHelper);
        CSVDataSet ds = CSVUtil.parse(new InputStreamReader(new FileInputStream(file), StringUtil.GBK), ',', true);
        String ctpInstrument=null;
        LocalDate ctpTradingDay=null;
        List<MarketData> ctpTicks = new ArrayList<>();
        while(ds.next()) {
            String row[] = new String[] {
                    ds.get("交易日")
                    ,ds.get("合约代码")
                    ,ds.get("交易所代码")
                    ,ds.get("合约在交易所的代码")
                    ,ds.get("最新价")
                    ,ds.get("上次结算价")
                    ,ds.get("昨收盘")
                    ,ds.get("昨持仓量")
                    ,ds.get("今开盘")
                    ,ds.get("最高价")
                    ,ds.get("最低价")
                    ,ds.get("数量")
                    ,ds.get("成交金额")
                    ,ds.get("持仓量")
                    ,ds.get("今收盘")
                    ,ds.get("本次结算价")
                    ,ds.get("涨停板价")
                    ,ds.get("跌停板价")
                    ,ds.get("昨虚实度")
                    ,ds.get("今虚实度")
                    ,ds.get("最后修改时间")
                    ,ds.get("最后修改毫秒")

                    ,ds.get("申买价一")
                    ,ds.get("申买量一")
                    ,ds.get("申卖价一")
                    ,ds.get("申卖量一")

                    ,ds.get("申买价二")
                    ,ds.get("申买量二")
                    ,ds.get("申卖价二")
                    ,ds.get("申卖量二")

                    ,ds.get("申买价三")
                    ,ds.get("申买量三")
                    ,ds.get("申卖价三")
                    ,ds.get("申卖量三")

                    ,ds.get("申买价四")
                    ,ds.get("申买量四")
                    ,ds.get("申卖价四")
                    ,ds.get("申卖量四")

                    ,ds.get("申买价五")
                    ,ds.get("申买量五")
                    ,ds.get("申卖价五")
                    ,ds.get("申卖量五")

                    ,ds.get("当日均价")
                    ,ds.get("业务日期")
            };
            CThostFtdcDepthMarketDataField ctpData = ctpCsvHelper.unmarshall(row);
            ctpInstrument = ctpData.InstrumentID;
            ctpTradingDay = DateUtil.str2localdate(ctpData.TradingDay);
            ctpTicks.add(new CtpMarketData(MarketDataProducer.PROVIDER_CTP, Future.fromString(ctpData.InstrumentID), ctpData, ctpTradingDay));
            ctpCsvWrite.next();
            ctpCsvWrite.marshall(ctpData);
        }
        Exchangeable ctpFuture = Exchangeable.fromString(ctpInstrument);
        //写入MIN1数据
        saveBars(data, ctpFuture, ExchangeableData.MIN1, ctpTradingDay, ctpTicks);
        //写入每天日线数据
        saveDayBars(data, ctpFuture, ctpTradingDay, ctpTicks);
        data.save(ctpFuture, ExchangeableData.TICK_CTP, ctpTradingDay, ctpCsvWrite.toString());
        List<LocalDate> tradingDays = new ArrayList<>();
        tradingDays.add(ctpTradingDay);
        RepositoryInstrumentStatsAction.updateInstrumentStats(data, null, ctpFuture, tradingDays);
        writer.println(DateUtil.date2str(LocalDateTime.now())+" "+file.getAbsolutePath()+" : "+ctpFuture+" "+ctpTradingDay+" ticks: "+ctpTicks.size());
    }

    /**
     * 从标准行情数据目录导入:
     *
     */
    private void importFromCtpDir() throws Exception
    {
        executorService = new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors(), Runtime.getRuntime().availableProcessors(), 5, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        File marketData = TraderHomeUtil.getDirectory(TraderHomeUtil.DIR_MARKETDATA);
        File trashDir = TraderHomeUtil.getDirectory(TraderHomeUtil.DIR_TRASH);

        writer.println("从行情数据目录导入: "+marketData.getAbsolutePath());writer.flush();
        for(File tradingDayDir: FileUtil.listSubDirs(marketData)) {
            LocalDate date = DateUtil.str2localdate(tradingDayDir.getName());
            if ( date==null ) {
                writer.println("忽略目录 "+tradingDayDir);
                continue;
            }
            writer.print("导入交易日 "+tradingDayDir.getName()+" :"); writer.flush();
            TreeMap<Exchangeable, List<CtpMarketDataInfo>> mdInfos = ctpLoadMdInfos(tradingDayDir);
            List<java.util.concurrent.Future> saveFutures = new ArrayList<>();
            for(Exchangeable e:mdInfos.keySet()) {
                CtpMarketDataInfo mdInfo = mdInfos.get(e).get(0);
                if( !ctpProviders.contains(mdInfo.producerType) ) {
                    throw new RuntimeException(e+" 不支持的数据类型: "+mdInfo.producerType);
                }
                //为每个品种找到最合适的文件
                List<MarketData> ticks = ctpMergeMdInfos(e, mdInfos.get(e));
                //实际导入
                java.util.concurrent.Future f = executorService.submit(()->{
                    importCtpMarketData(date, mdInfo, ticks);
                    return null;
                });
                saveFutures.add(f);
            }
            for(java.util.concurrent.Future f:saveFutures) {
                f.get();
            }
            writer.println();
            //将每日目录转移trash目录中
            if ( moveToTrash ) {
                moveToTrash(trashDir, tradingDayDir);
            }
        }
    }

    private void moveToTrash(File trashDir, File dailyDir) throws IOException
    {
        trashDir.mkdirs();
        Files.move(dailyDir, new File(trashDir, dailyDir.getName()));
    }

    /**
     * 按天合并多处数据源的数据
     */
    private List<MarketData> ctpMergeMdInfos(Exchangeable e, List<CtpMarketDataInfo> mdInfos) throws Exception
    {
        //并行加载
        List<java.util.concurrent.Future<CtpMarketDataInfo>> futures = new ArrayList<>();
        for(CtpMarketDataInfo mdInfo:mdInfos) {
            java.util.concurrent.Future<CtpMarketDataInfo> f = executorService.submit(()->{
                CtpMarketDataInfo result = null;
                try{
                    mdInfo.load();
                    result = mdInfo;
                }catch(Throwable t) {
                     System.out.println("加载CTP TICK文件失败: "+mdInfo.tickFile+" : "+t);
                }
                return result;
            });
            futures.add(f);
        }
        List<CtpMarketDataInfo> mdInfos0 = new ArrayList<>();
        for(int i=0;i<futures.size();i++) {
            CtpMarketDataInfo mdInfo = futures.get(i).get();
            if ( null!=mdInfo ) {
                mdInfos0.add(mdInfo);
            }
        }
        List<List<MarketData>> allTicks = new ArrayList<>(mdInfos.size());
        for(CtpMarketDataInfo mdInfo:mdInfos) {
            allTicks.add(mdInfo.ticks);
        }
        List<MarketData> result = mergeAllTicks(e, allTicks);
        for(CtpMarketDataInfo mdInfo:mdInfos) {
            mdInfo.clear();
        }
        return result;
    }

    private List<MarketData> mergeAllTicks(Exchangeable e, List<List<MarketData>> allTicks){
        if ( e.exchange()==Exchange.DCE ) {
            for(List<MarketData> ticks:allTicks) {
                for(MarketData tick:ticks) {
                    int nano = tick.updateTime.getNano();
                    if ( nano< 500*1000*1000 ) {
                        nano = 0;
                    } else {
                        nano = 500*1000*1000;
                    }
                    tick.updateTime = tick.updateTime.withNano(nano);
                }
            }
        }
        if ( e.exchange()==Exchange.CZCE ) {
            //CZCE的UpdateMillisec始终是0, 不准, 需要重新校准.
            //DCE的UpdateTime是200,700这样的数, 也需要重新校准
            TreeMap<LocalDateTime, List<List<MarketData>>> allTickBySeconds = new TreeMap<>();
            for(List<MarketData> ticks:allTicks) {
                TreeMap<LocalDateTime, List<MarketData>> todayTickBySeconds = new TreeMap<>();
                for(MarketData tick:ticks) {
                    LocalDateTime timestamp0 = tick.updateTime.withNano(0);
                    List<MarketData> secondTicks = todayTickBySeconds.get(timestamp0);
                    if (null==secondTicks) {
                        secondTicks = new ArrayList<>(2);
                        todayTickBySeconds.put(timestamp0, secondTicks);
                    }
                    secondTicks.add(tick);
                }
                for(LocalDateTime timestamp:todayTickBySeconds.keySet()) {
                    List<MarketData> secondTicks = todayTickBySeconds.get(timestamp);
                    List<List<MarketData>> allSecondTicks = allTickBySeconds.get(timestamp);
                    if (null==allSecondTicks) {
                        allSecondTicks = new ArrayList<>(2);
                        allTickBySeconds.put(timestamp, allSecondTicks);
                    }
                    allSecondTicks.add(secondTicks);
                }
            }
            List<MarketData> result = new ArrayList<>(50000);
            for(LocalDateTime timestamp:allTickBySeconds.keySet()) {
                List<List<MarketData>> allSecondTicks = allTickBySeconds.get(timestamp);
                result.addAll( mergeSecondTicks_CZCE(allSecondTicks) );
            }
            return result;
        } else {
            TreeMap<LocalDateTime, MarketData> ticksByTime = new TreeMap<>();
            for(List<MarketData> ticks:allTicks) {
                for(MarketData tick:ticks) {
                    if ( !ticksByTime.containsKey(tick.updateTime) || (PriceUtil.isValidPrice(tick.openPrice) && PriceUtil.isValidPrice(tick.lastPrice))) {
                        ticksByTime.put(tick.updateTime, tick);
                    }
                }
            }
            return new ArrayList<>(ticksByTime.values());
        }
    }

    /**
     * 合并按秒的CZCE的分笔数据
     */
    private List<MarketData> mergeSecondTicks_CZCE(List<List<MarketData>> allSecondTicks){
        //#1 查找是否单一数据源有两个TICK
        List<MarketData> ticks = null;
        {
            for(List<MarketData> secondTicks : allSecondTicks) {
                if ( secondTicks.size()>=2 || (ticks!=null && secondTicks.size()>ticks.size()) ) {
                    ticks = secondTicks;
                }
            }
        }
        if (null==ticks){
            //两个都是1个的, 选第一个就好.
            ticks = allSecondTicks.get(0);
        }
        List<MarketData> result = new ArrayList<>();
        MarketData tick0 = null;
        for(MarketData tick:ticks) {
            if ( tick0!=null && secondTickEquals(tick0, tick) ) {
                continue;
            }
            tick0 = tick;
            result.add(tick);
        }
        return result;
    }

    /**
     * 存档行情数据
     */
    private void importCtpMarketData(LocalDate date, CtpMarketDataInfo mdInfo, List<MarketData> mergedTicks) throws IOException
    {
        DataInfo dataInfo = ExchangeableData.TICK_CTP;
        CSVMarshallHelper csvMarshallHelper = createCSVMarshallHelper(mdInfo.producerType);
        MarketDataProducer mdProducer = createMarketDataProducer(mdInfo.producerType);

        mdInfo.tickCount = 0;
        mdInfo.savedTicks = 0;
        int existsCount = 0;
        String existsData = null;
        //先加载当天已有的TICK数据
        if ( data.exists(mdInfo.exchangeable, dataInfo, date) && merge) {
            List<MarketData> ticks0 = new ArrayList<>(80000);
            existsData = data.load(mdInfo.exchangeable, dataInfo, date);
            CSVDataSet csvDataSet = CSVUtil.parse(existsData);
            while(csvDataSet.next()) {
                MarketData tick = mdProducer.createMarketData(csvMarshallHelper.unmarshall(csvDataSet.getRow()), mdInfo.tradingDay);
                ticks0.add(tick);
            }
            existsCount = ticks0.size();
            List<List<MarketData>> allTicks = new ArrayList<>();
            allTicks.add(ticks0);
            allTicks.add(mergedTicks);
            mergedTicks = mergeAllTicks(mdInfo.exchangeable, allTicks);
        }
        mdInfo.tickCount = mergedTicks.size();
        mdInfo.savedTicks = mdInfo.tickCount-existsCount;
        CSVWriter csvWriter = new CSVWriter<>(csvMarshallHelper);
        for(MarketData tick:mergedTicks) {
            if ( tick instanceof CtpMarketData ) {
                var tick0 = (CtpMarketData)tick;
                csvWriter.next().setRow(csvMarshallHelper.marshall(tick0.field));
            }else if (tick instanceof WebMarketData ) {
                var tick0 = (WebMarketData)tick;
                csvWriter.next().setRow(csvMarshallHelper.marshall(tick0.field));
            }
        }
        //保存上个交易日结算价

        String mergedData = csvWriter.toString();
        if ( mergedTicks.size()>0 && !StringUtil.equals(mergedData, existsData) ) {
            data.save(mdInfo.exchangeable, dataInfo, date, mergedData);
            //写入MIN1数据
            saveBars(data, mdInfo.exchangeable, ExchangeableData.MIN1, date, mergedTicks);
            //写入每天日线数据
            saveDayBars(data, mdInfo.exchangeable, date, mergedTicks);
            List<LocalDate> tradingDays = new ArrayList<>();
            tradingDays.add(mdInfo.tradingDay);
            RepositoryInstrumentStatsAction.updateInstrumentStats(data, null, mdInfo.exchangeable, tradingDays);
            writer.print(" "+mdInfo.exchangeable+" "+mdInfo.savedTicks+"/"+mdInfo.tickCount); writer.flush();
        }
    }

    /**
     * 将原始日志保存为按天数据
     */
    public static void saveDayBars(ExchangeableData data, Exchangeable instrument, LocalDate tradingDay, List<MarketData> ticks) throws IOException
    {
        DataInfo day = ExchangeableData.DAY;
        List<FutureBarImpl> bars2 = BarSeriesLoader.marketDatas2bars(instrument, tradingDay, day.getLevel(), ticks);
        long preSettlementPrice = ticks.get(ticks.size()-1).preSettlementPrice;
        saveDayBars2(data, instrument, tradingDay, bars2, preSettlementPrice);
    }

    public static void saveDayBars2(ExchangeableData data, Exchangeable instrument, LocalDate tradingDay, List<FutureBarImpl> bars2, long preSettlementPrice) throws IOException
    {
        DataInfo day = ExchangeableData.DAY;
        if (bars2.isEmpty() ) {
            return;
        }
        LocalDate preTradingDay = MarketDayUtil.prevMarketDay(instrument.exchange(), tradingDay);
        CSVWriter csvWriter = new CSVWriter(day.getColumns());
        if ( data.exists(instrument, ExchangeableData.DAY, null)) {
            CSVDataSet csvDataSet = CSVUtil.parse(data.load(instrument, day, tradingDay));
            csvWriter.fromDataSetAll(csvDataSet);
        }
        FutureBarImpl bar2 = bars2.get(0);
        csvWriter.next();
        bar2.saveDay(csvWriter);
        //设置上一日结算价
        if ( preSettlementPrice!=0 && null!=preTradingDay) {
            String preday = DateUtil.date2str(preTradingDay);
            csvWriter.forEach((String[] row)->{
                String date = row[csvWriter.getColumnIndex(ExchangeableData.COLUMN_DATE)];
                if ( StringUtil.equals(preday, date)) {
                    row[csvWriter.getColumnIndex(ExchangeableData.COLUMN_SETTLEMENT_PRICE)] = PriceUtil.long2str(preSettlementPrice);
                }
                return true;
            });
        }
        csvWriter.merge(true, ExchangeableData.COLUMN_DATE);
        data.save(instrument, day, null, csvWriter.toString());
    }

    /**
     * 将原始日志统计为MIN1.
     * @param marketDatas 当日全部TICK数据
     */
    public static void saveBars(ExchangeableData data, Exchangeable instrument, DataInfo dataInfo, LocalDate tradingDay, List<MarketData> marketDatas) throws IOException
    {
        List<FutureBarImpl> bars = BarSeriesLoader.marketDatas2bars(instrument, tradingDay, dataInfo.getLevel(), marketDatas);
        saveBars2(data, instrument, dataInfo, tradingDay, bars);
    }

    public static void saveBars2(ExchangeableData data, Exchangeable instrument, DataInfo dataInfo, LocalDate tradingDay, List<FutureBarImpl> bars) throws IOException
    {
        CSVWriter csvWriter = new CSVWriter(dataInfo.getColumns());
        //MIN1始终完全重新生成
        for(Bar bar:bars) {
            csvWriter.next();
            if ( bar instanceof FutureBarImpl ) {
                ((FutureBarImpl)bar).save(csvWriter);
            } else {
                csvWriter.set(ExchangeableData.COLUMN_BEGIN_TIME, DateUtil.date2str(bar.getBeginTime().toLocalDateTime()));
                csvWriter.set(ExchangeableData.COLUMN_END_TIME, DateUtil.date2str(bar.getEndTime().toLocalDateTime()));
                csvWriter.set(ExchangeableData.COLUMN_OPEN, bar.getOpenPrice().toString());
                csvWriter.set(ExchangeableData.COLUMN_HIGH, bar.getHighPrice().toString());
                csvWriter.set(ExchangeableData.COLUMN_LOW, bar.getLowPrice().toString());
                csvWriter.set(ExchangeableData.COLUMN_CLOSE, bar.getClosePrice().toString());

                csvWriter.set(ExchangeableData.COLUMN_VOLUME, ""+bar.getVolume().longValue());
                csvWriter.set(ExchangeableData.COLUMN_AMOUNT, bar.getAmount().toString());
            }
        }
        //保存
        data.save(instrument, dataInfo, tradingDay, csvWriter.toString());
    }

    /**
     * 依次加载和检测行情数据信息
     */
    private TreeMap<Exchangeable, List<CtpMarketDataInfo>> ctpLoadMdInfos(File tradingDayDir) throws Exception
    {
        LocalDate tradingDay = DateUtil.str2localdate(tradingDayDir.getName());
        TreeMap<Exchangeable, List<CtpMarketDataInfo>> result = new TreeMap<>();
        for(File producerDir : FileUtil.listSubDirs(tradingDayDir)) {
            if ( producerDir.getName().equals("merged")) {
                continue;
            }
            String producerType = detectProducerType(producerDir);
            for(File csvFile:producerDir.listFiles()) {
                if( !csvFile.getName().endsWith(".csv") ) {
                    continue;
                }
                CtpMarketDataInfo mdInfo = loadMarketDataInfo(tradingDay, csvFile, producerType);
                if ( mdInfo==null ) {
                    continue;
                }
                List<CtpMarketDataInfo> mdInfos = result.get(mdInfo.exchangeable);
                if ( mdInfos==null ) {
                    mdInfos = new ArrayList<>();
                    result.put(mdInfo.exchangeable, mdInfos);
                }
                mdInfos.add(mdInfo);
            }
        }
        return result;
    }

    /**
     * 加载producer.json文件, 检测producer类型
     */
    String detectProducerType(File producerDir) throws IOException
    {
        String result = MarketDataProducer.PROVIDER_CTP;
        File producerJson = new File(producerDir, "producer.json");
        if (producerJson.exists()) {
            JsonObject json = (JsonObject) JsonParser.parseString(FileUtil.read(producerJson));
            JsonElement typeElem = json.get("type");
            if ( typeElem!=null ) {
                result = typeElem.getAsString();
            }
            JsonElement providerElem = json.get("provider");
            if ( providerElem!=null ) {
                result = providerElem.getAsString();
            }
        }
        return result;
    }

    /**
     * 加载和转换行情数据.
     * <BR>这个函数会排除开始和结束时间不对的数据
     */
    private CtpMarketDataInfo loadMarketDataInfo(LocalDate tradingDay, File csvFile, String producerType) throws IOException
    {
        CtpMarketDataInfo result = new CtpMarketDataInfo(tradingDay, csvFile, producerType);
        return result;
    }

    private CSVMarshallHelper createCSVMarshallHelper(String producerType) {
        MarketDataProducerFactory factory = producerFactories.get(producerType);
        if ( factory!=null ) {
            return factory.createCSVMarshallHelper();
        }
        return null;
    }

    private MarketDataProducer createMarketDataProducer(String producerType) {
        MarketDataProducerFactory factory = producerFactories.get(producerType);
        if ( factory!=null ) {
            return factory.create(null, Collections.emptyMap());
        }
        return null;
    }

    private void parseOptions(List<KVPair> options) {
        for(KVPair kv:options) {
            switch(kv.k.toLowerCase()) {
            case "producer":
                this.producer = kv.v;
                break;
            case "datadir":
                this.dataDir = kv.v;
                break;
            case "move":
                if ( StringUtil.equalsIgnoreCase(kv.v, "trash") ) {
                    moveToTrash = true;
                }
                break;
            case "merge":
                merge = ConversionUtil.toBoolean(kv.v);
                break;
            case "instrument":
                instrumentFilters.add(kv.v);
                break;
            case "instruments":
                for(String p:StringUtil.split(kv.v, ",|;")) {
                    instrumentFilters.add(p);
                }
                break;
            }
        }
    }

    private boolean acceptInstrument(Exchangeable instrument) {
        if ( instrumentFilters.isEmpty() ) {
            return true;
        }
        for(String f:instrumentFilters) {
            if ( instrument.uniqueId().indexOf(f)>=0 ) {
                return true;
            }
        }
        return false;
    }

    /**
     * 比较两个Tick的时间戳
     */
    private int secondTickTimeCompare(MarketData tick1, MarketData tick2) {
        int timeCompare = tick1.updateTime.compareTo(tick2.updateTime);
        if ( timeCompare!=0 ) {
            return timeCompare;
        }
        if ( tick1.volume>tick2.volume || tick1.turnover>tick2.turnover ) {
            return 1;
        }
        if ( tick1.volume<tick2.volume || tick1.turnover<tick2.turnover ) {
            return -1;
        }
        return 0;
    }

    private boolean secondTickEquals(MarketData tick1, MarketData tick2) {
        if ( tick1.volume== tick2.volume
                && tick1.turnover==tick2.turnover
                && tick1.openInterest==tick2.openInterest
                && tick1.lastPrice == tick2.lastPrice
                && tick1.askPrices[0] == tick2.askPrices[0]
                && tick1.askVolumes[0] == tick2.askVolumes[0]
                && tick1.bidPrices[0] == tick2.bidPrices[0]
                && tick1.bidVolumes[0] == tick2.bidVolumes[0] )
        {
            return true;
        }
        return false;
    }

}
