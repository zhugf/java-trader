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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.ta4j.core.Bar;

import com.google.common.io.Files;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.jctp.CThostFtdcDepthMarketDataField;
import trader.common.beans.BeansContainer;
import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableData;
import trader.common.exchangeable.ExchangeableData.DataInfo;
import trader.common.exchangeable.ExchangeableTradingTimes;
import trader.common.exchangeable.Future;
import trader.common.exchangeable.MarketTimeStage;
import trader.common.util.CSVDataSet;
import trader.common.util.CSVMarshallHelper;
import trader.common.util.CSVUtil;
import trader.common.util.CSVWriter;
import trader.common.util.ConversionUtil;
import trader.common.util.DateUtil;
import trader.common.util.FileUtil;
import trader.common.util.StringUtil;
import trader.common.util.StringUtil.KVPair;
import trader.common.util.TraderHomeUtil;
import trader.common.util.csv.CtpCSVMarshallHelper;
import trader.service.md.MarketData;
import trader.service.md.MarketDataProducer;
import trader.service.md.MarketDataProducerFactory;
import trader.service.md.ctp.CtpMarketData;
import trader.service.ta.FutureBar;
import trader.service.ta.TimeSeriesLoader;
import trader.service.util.CmdAction;
import trader.simulator.SimMarketDataService;

/**
 * 行情数据的归档命令.
 * <BR>行情数据的临时保存的目录结构: TraderHome/marketData/20181010/mdProducerId/shfe.ru1901.csv
 */
public class MarketDataImportAction implements CmdAction {

    private static class MarketDataInfo implements Comparable<MarketDataInfo>{
        LocalDate tradingDay;
        Exchangeable exchangeable;
        File tickFile;
        List<MarketData> ticks;
        String producerType = MarketDataProducer.PROVIDER_CTP;
        /**
         * tick数量, 去除交易时间段之外的tick, 去除重复的tick
         */
        int tickCount;
        /**
         * 保存tick数量
         */
        int savedTicks;

        @Override
        public int compareTo(MarketDataInfo o) {
            return tickCount-o.tickCount;
        }
    }
    private PrintWriter writer;
    private ExchangeableData data;
    private Map<String, MarketDataProducerFactory> producerFactories;
    private String producer;
    private String dataDir;
    private boolean moveToTrash;
    private boolean force;

    @Override
    public String getCommand() {
        return "marketData.import";
    }

    @Override
    public void usage(PrintWriter writer) {
        writer.println("marketData import [--producer=ctp|jinshuyuan|sqlite] [--datadir=DATA_DIR] [--move=trash|none] [--force=true|false]");
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
            importJinshuyuan();
        } else if ( StringUtil.equals(producer, "sqlite")) {
            importSqlites();
        } else {
            importFromDataDir();
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
                if ( !force && data.exists(instrument, ExchangeableData.TICK_CTP, tradingDay) ) {
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

    /**
     * 金数源 目录
     */
    private void importJinshuyuan() throws Exception
    {
        File mdDir = new File(dataDir);
        writer.println("从目录导入: "+mdDir.getAbsolutePath());writer.flush();
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
            data.save(ctpFuture, ExchangeableData.TICK_CTP, ctpTradingDay, ctpCsvWrite.toString());
            saveDayBars(data, ctpFuture, ctpTradingDay, ctpTicks);
            writer.println(file.getAbsolutePath()+" : "+ctpFuture+" "+ctpTradingDay);
        }
    }

    /**
     * 从标准行情数据目录导入
     */
    private void importFromDataDir() throws Exception
    {
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
            LinkedHashMap<Exchangeable, List<MarketDataInfo>> marketDataInfos = loadMarketDataInfos(tradingDayDir);
            List<Exchangeable> exchangeables = new ArrayList<>(marketDataInfos.keySet());
            Collections.sort(exchangeables);
            for(Exchangeable e:exchangeables) {
                //为每个品种找到最合适的文件
                List<MarketDataInfo> mdInfos = marketDataInfos.get(e);
                Collections.sort(mdInfos);
                //实际导入
                MarketDataInfo mdInfo = mdInfos.get(mdInfos.size()-1);
                importMarketData(date, mdInfo);
                writer.print(" "+mdInfo.exchangeable+"("+mdInfo.savedTicks+"/"+mdInfo.tickCount+")"); writer.flush();
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
     * 存档行情数据
     */
    private void importMarketData(LocalDate date, MarketDataInfo mdInfo) throws IOException
    {
        DataInfo dataInfo = null;
        if( mdInfo.producerType.equalsIgnoreCase(ExchangeableData.TICK_CTP.provider())) {
            dataInfo = ExchangeableData.TICK_CTP;
        }else{
            throw new RuntimeException("不支持的数据类型: "+mdInfo.producerType);
        }
        CSVMarshallHelper csvMarshallHelper = createCSVMarshallHelper(mdInfo.producerType);
        MarketDataProducer mdProducer = createMarketDataProducer(mdInfo.producerType);

        List<MarketData> ticks = new ArrayList<>();
        Set<LocalDateTime> existsTimes = new TreeSet<>();
        CSVWriter csvWriter = new CSVWriter<>(csvMarshallHelper);
        //先加载当天已有的TICK数据
        if ( data.exists(mdInfo.exchangeable, dataInfo, date) ) {
            String csvText = data.load(mdInfo.exchangeable, dataInfo, date);
            CSVDataSet csvDataSet = CSVUtil.parse(csvText);
            while(csvDataSet.next()) {
                MarketData marketData = mdProducer.createMarketData(csvMarshallHelper.unmarshall(csvDataSet.getRow()), mdInfo.tradingDay);
                ticks.add(marketData);
                existsTimes.add(marketData.updateTime);
                csvWriter.next().setRow(csvDataSet.getRow());
            }
        }
        //再写入TICK数据
        CSVDataSet csvDataSet = CSVUtil.parse(FileUtil.read(mdInfo.tickFile));
        while(csvDataSet.next()) {
            MarketData md = mdProducer.createMarketData(csvMarshallHelper.unmarshall(csvDataSet.getRow()), mdInfo.tradingDay);
            if ( existsTimes.contains(md.updateTime)) {
                continue;
            }
            Exchangeable e = md.instrument;
            ExchangeableTradingTimes mdTradingTimes = e.exchange().getTradingTimes(e, DateUtil.str2localdate(md.tradingDay));
            if ( mdTradingTimes==null || mdTradingTimes.getTimeStage(md.updateTime)!=MarketTimeStage.MarketOpen ) {
                continue;
            }
            ticks.add(md);
            csvWriter.next().setRow(csvDataSet.getRow());
            mdInfo.savedTicks++;
        }
        if ( mdInfo.savedTicks>0 ) {
            data.save(mdInfo.exchangeable, dataInfo, date, csvWriter.toString());
            //写入MIN1数据
            saveMin1Bars(data, mdInfo.exchangeable, date, ticks);
            //写入每天日线数据
            saveDayBars(data, mdInfo.exchangeable, date, ticks);
        }
    }

    /**
     * 将原始日志保存为按天数据
     */
    public static void saveDayBars(ExchangeableData data, Exchangeable instrument, LocalDate tradingDay, List<MarketData> ticks) throws IOException
    {
        DataInfo day = ExchangeableData.DAY;
        CSVWriter csvWriter = new CSVWriter(day.getColumns());
        if ( data.exists(instrument, ExchangeableData.DAY, null)) {
            CSVDataSet csvDataSet = CSVUtil.parse(data.load(instrument, day, tradingDay));
            csvWriter.fromDataSetAll(csvDataSet);
        }

        List<FutureBar> bars2 = TimeSeriesLoader.marketDatas2bars(instrument, tradingDay, day.getLevel(), ticks);
        if (bars2.isEmpty() ) {
            return;
        }
        FutureBar bar2 = bars2.get(0);
        String[] row2 = new String[day.getColumns().length];
        row2[0] = DateUtil.date2str(tradingDay);
        row2[1] = ""+bar2.getOpenPrice();
        row2[2] = ""+bar2.getMaxPrice();
        row2[3] = ""+bar2.getMinPrice();
        row2[4] = ""+bar2.getClosePrice();

        row2[5] = ""+bar2.getVolume();
        row2[6] = ""+bar2.getAmount();
        row2[7] = ""+bar2.getOpenInterest();

        csvWriter.next().setRow(row2);
        csvWriter.merge(true, ExchangeableData.COLUMN_DATE);

        data.save(instrument, day, null, csvWriter.toString());
    }

    /**
     * 将原始日志统计为MIN1.
     * @param marketDatas 当日全部TICK数据
     */
    public static void saveMin1Bars(ExchangeableData data, Exchangeable instrument, LocalDate tradingDay, List<MarketData> marketDatas) throws IOException
    {
        DataInfo dataInfo = ExchangeableData.MIN1;

        List<FutureBar> bars = TimeSeriesLoader.marketDatas2bars(instrument, tradingDay, dataInfo.getLevel(), marketDatas);
        CSVWriter csvWriter = new CSVWriter(dataInfo.getColumns());
        //MIN1始终完全重新生成
        for(Bar bar:bars) {
            csvWriter.next();
            if ( bar instanceof FutureBar ) {
                ((FutureBar)bar).save(csvWriter);
            } else {
                csvWriter.set(ExchangeableData.COLUMN_BEGIN_TIME, DateUtil.date2str(bar.getBeginTime().toLocalDateTime()));
                csvWriter.set(ExchangeableData.COLUMN_END_TIME, DateUtil.date2str(bar.getEndTime().toLocalDateTime()));
                csvWriter.set(ExchangeableData.COLUMN_OPEN, bar.getOpenPrice().toString());
                csvWriter.set(ExchangeableData.COLUMN_HIGH, bar.getMaxPrice().toString());
                csvWriter.set(ExchangeableData.COLUMN_LOW, bar.getMinPrice().toString());
                csvWriter.set(ExchangeableData.COLUMN_CLOSE, bar.getClosePrice().toString());

                csvWriter.set(ExchangeableData.COLUMN_VOLUME, ""+bar.getVolume().longValue());
                csvWriter.set(ExchangeableData.COLUMN_TURNOVER, bar.getAmount().toString());
            }
        }
        //保存
        data.save(instrument, dataInfo, tradingDay, csvWriter.toString());
    }

    /**
     * 依次加载和检测行情数据信息
     */
    private LinkedHashMap<Exchangeable, List<MarketDataInfo>> loadMarketDataInfos(File tradingDayDir) throws Exception
    {
        LocalDate tradingDay = DateUtil.str2localdate(tradingDayDir.getName());
        LinkedHashMap<Exchangeable, List<MarketDataInfo>> result = new LinkedHashMap<>();
        for(File producerDir : FileUtil.listSubDirs(tradingDayDir)) {
            String producerType = detectProducerType(producerDir);
            for(File csvFile:producerDir.listFiles()) {
                if( !csvFile.getName().endsWith(".csv") ) {
                    continue;
                }
                MarketDataInfo mdInfo = loadMarketDataInfo(tradingDay, csvFile, producerType);
                if ( mdInfo==null ) {
                    continue;
                }
                List<MarketDataInfo> mdInfos = result.get(mdInfo.exchangeable);
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
            JsonObject json = (JsonObject) (new JsonParser()).parse(FileUtil.read(producerJson));
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
    private MarketDataInfo loadMarketDataInfo(LocalDate tradingDay, File csvFile, String producerType) throws IOException
    {
        MarketDataInfo result = new MarketDataInfo();
        result.producerType = producerType;
        result.tickFile = csvFile;
        result.tradingDay = tradingDay;

        CSVMarshallHelper csvMarshallHelper = createCSVMarshallHelper(producerType);
        MarketDataProducer mdProducer = createMarketDataProducer(producerType);

        ExchangeableTradingTimes tradingTimes = null;
        CSVDataSet csvDataSet = CSVUtil.parse(FileUtil.read(csvFile));
        while(csvDataSet.next()) {
            MarketData md = mdProducer.createMarketData(csvMarshallHelper.unmarshall(csvDataSet.getRow()), tradingDay);
            Exchangeable e = md.instrument;
            result.exchangeable = e;
            if ( tradingTimes==null ) {
                tradingTimes = e.exchange().getTradingTimes(e, tradingDay);
            }
            if ( tradingTimes==null || tradingTimes.getTimeStage(md.updateTime)!=MarketTimeStage.MarketOpen ) {
                continue;
            }
            result.tickCount++; //只计算正式开市的数据
        }

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
            case "force":
                force = ConversionUtil.toBoolean(kv.v);
                break;
            }
        }
    }
}
