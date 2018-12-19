package trader.tool;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import org.ta4j.core.Bar;

import com.google.common.io.Files;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import trader.common.exchangeable.Exchange.MarketType;
import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableData;
import trader.common.exchangeable.ExchangeableData.DataInfo;
import trader.common.exchangeable.MarketTimeStage;
import trader.common.exchangeable.TradingMarketInfo;
import trader.common.util.*;
import trader.service.md.MarketData;
import trader.service.md.MarketDataProducer;
import trader.service.md.MarketDataProducerFactory;
import trader.service.ta.FutureBar;
import trader.service.ta.TimeSeriesLoader;
import trader.simulator.SimMarketDataService;

/**
 * 行情数据的归档命令.
 * <BR>行情数据的临时保存的目录结构: TraderHome/marketData/20181010/mdProducerId/shfe.ru1901.csv
 */
public class MarketDataImportAction implements CmdAction {

    private static class MarketDataInfo implements Comparable<MarketDataInfo>{
        LocalDate tradingDay;
        Exchangeable exchangeable;
        File marketDataFile;
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

    private ExchangeableData exchangeableData;
    private Map<String, MarketDataProducerFactory> producerFactories;

    @Override
    public String getCommand() {
        return "data.import";
    }

    @Override
    public void usage(PrintWriter writer) {
        writer.println("marketData import");
        writer.println("\t导入行情数据");
    }

    @Override
    public int execute(PrintWriter writer, List<String> options) throws Exception
    {
        producerFactories = SimMarketDataService.discoverProducerFactories();
        File marketData = TraderHomeUtil.getDirectory(TraderHomeUtil.DIR_MARKETDATA);
        File trashDir = TraderHomeUtil.getDirectory(TraderHomeUtil.DIR_TRASH);
        writer.println("从行情数据目录导入: "+marketData.getAbsolutePath());writer.flush();
        exchangeableData = new ExchangeableData(TraderHomeUtil.getDirectory(TraderHomeUtil.DIR_REPOSITORY), false);
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
            moveToTrash(trashDir, tradingDayDir);
        }
        return 0;
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
        DataInfo dataInfo = ExchangeableData.TICK_CTP;
        switch(mdInfo.producerType) {
        case MarketDataProducer.PROVIDER_CTP:
            dataInfo = ExchangeableData.TICK_CTP;
            break;
        default:
            throw new RuntimeException("不支持的数据类型: "+mdInfo.producerType);
        }
        CSVMarshallHelper csvMarshallHelper = createCSVMarshallHelper(mdInfo.producerType);
        MarketDataProducer mdProducer = createMarketDataProducer(mdInfo.producerType);

        List<MarketData> allMarketDatas = new ArrayList<>();
        Set<LocalDateTime> existsTimes = new TreeSet<>();
        CSVWriter csvWriter = new CSVWriter<>(csvMarshallHelper);
        //先加载当天已有的TICK数据
        if ( exchangeableData.exists(mdInfo.exchangeable, dataInfo, date) ) {
            String csvText = exchangeableData.load(mdInfo.exchangeable, dataInfo, date);
            CSVDataSet csvDataSet = CSVUtil.parse(csvText);
            while(csvDataSet.next()) {
                MarketData marketData = mdProducer.createMarketData(csvMarshallHelper.unmarshall(csvDataSet.getRow()), mdInfo.tradingDay);
                allMarketDatas.add(marketData);
                existsTimes.add(marketData.updateTime);
                csvWriter.next().setRow(csvDataSet.getRow());
            }
        }
        //再写入TICK数据
        CSVDataSet csvDataSet = CSVUtil.parse(FileUtil.read(mdInfo.marketDataFile));
        while(csvDataSet.next()) {
            MarketData marketData = mdProducer.createMarketData(csvMarshallHelper.unmarshall(csvDataSet.getRow()), mdInfo.tradingDay);
            if ( existsTimes.contains(marketData.updateTime)) {
                continue;
            }
//            String line = csvDataSet.getLine();
//            if ( line.indexOf("20181213,au1906,,,281.40,281.70,281.20,243264.00,281.40,281.40,281.40,90,25326000.00,243220.00,N/A,N/A,292.95,270.40,N/A,N/A,21:00:00,500,281.35,75,281.40,27,N/A,0,N/A,0,N/A,0,N/A,0,N/A,0,N/A,0,N/A,0,N/A,0,281400.00,20181212")>=0) {
//                System.out.println("TO BREAK");
//            }
            TradingMarketInfo tradingMarketInfo = marketData.instrumentId.detectTradingMarketInfo(marketData.updateTime);
            if ( tradingMarketInfo==null || tradingMarketInfo.getStage()!=MarketTimeStage.MarketOpen ) {
                continue;
            }
            if ( csvDataSet.getRowIndex()<=2 && tradingMarketInfo.getTradingTime()>3600*1000 ) {
                continue;
            }
            allMarketDatas.add(marketData);
            csvWriter.next().setRow(csvDataSet.getRow());
            mdInfo.savedTicks++;
        }
        if ( mdInfo.savedTicks>0 ) {
            exchangeableData.save(mdInfo.exchangeable, dataInfo, date, csvWriter.toString());
            //写入MIN1数据
            saveMin1Bars(date, mdInfo, allMarketDatas);
        }

    }

    /**
     * 将原始日志统计为MIN1.
     *
     * @param marketDatas 当日全部TICK数据
     */
    private void saveMin1Bars(LocalDate date, MarketDataInfo mdInfo, List<MarketData> marketDatas) throws IOException
    {
        DataInfo dataInfo = ExchangeableData.MIN1;

        List<Bar> bars = TimeSeriesLoader.marketDatas2bars(mdInfo.exchangeable, dataInfo.getLevel(), marketDatas);
        CSVWriter csvWriter = new CSVWriter(dataInfo.getColumns());
        //MIN1始终完全重新生成
        for(Bar bar:bars) {
            csvWriter.next();
            csvWriter.set(ExchangeableData.COLUMN_BEGIN_TIME, DateUtil.date2str(bar.getBeginTime().toLocalDateTime()));
            csvWriter.set(ExchangeableData.COLUMN_END_TIME, DateUtil.date2str(bar.getEndTime().toLocalDateTime()));
            csvWriter.set(ExchangeableData.COLUMN_OPEN, PriceUtil.long2str(bar.getOpenPrice().longValue()));
            csvWriter.set(ExchangeableData.COLUMN_HIGH, PriceUtil.long2str(bar.getMaxPrice().longValue()));
            csvWriter.set(ExchangeableData.COLUMN_LOW, PriceUtil.long2str(bar.getMinPrice().longValue()));
            csvWriter.set(ExchangeableData.COLUMN_CLOSE, PriceUtil.long2str(bar.getClosePrice().longValue()));

            csvWriter.set(ExchangeableData.COLUMN_VOLUME, Long.toString(bar.getVolume().longValue()));
            csvWriter.set(ExchangeableData.COLUMN_TURNOVER, PriceUtil.long2str(bar.getAmount().longValue()));
            if ( bar instanceof FutureBar ) {
                csvWriter.set(ExchangeableData.COLUMN_OPENINT, Long.toString(((FutureBar)bar).getOpenInterest().longValue()));
            }
        }
        //保存
        exchangeableData.save(mdInfo.exchangeable, dataInfo, date, csvWriter.toString());
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
        result.marketDataFile = csvFile;
        result.tradingDay = tradingDay;


        CSVMarshallHelper csvMarshallHelper = createCSVMarshallHelper(producerType);
        MarketDataProducer mdProducer = createMarketDataProducer(producerType);

        Map<MarketType, LocalDateTime[]> mdBegineEndTimes = new HashMap<>();

        CSVDataSet csvDataSet = CSVUtil.parse(FileUtil.read(csvFile));
        while(csvDataSet.next()) {
            MarketData marketData = mdProducer.createMarketData(csvMarshallHelper.unmarshall(csvDataSet.getRow()), null);
            result.exchangeable = marketData.instrumentId;
            TradingMarketInfo tradingMarketInfo = marketData.instrumentId.detectTradingMarketInfo(marketData.updateTime);
            if ( tradingMarketInfo==null || tradingMarketInfo.getStage()!=MarketTimeStage.MarketOpen ) {
                continue;
            }
            //设置日市夜市的行情开始/结束时间
            LocalDateTime[] mdBeginEndTime = mdBegineEndTimes.get(tradingMarketInfo.getMarket());
            if ( mdBeginEndTime==null) {
                mdBeginEndTime = new LocalDateTime[4];
                mdBeginEndTime[0] = tradingMarketInfo.getMarketOpenTime();
                mdBeginEndTime[1] = tradingMarketInfo.getMarketCloseTime();
                mdBeginEndTime[2] = marketData.updateTime;
                mdBegineEndTimes.put(tradingMarketInfo.getMarket(), mdBeginEndTime);
            }
            mdBeginEndTime[3] = marketData.updateTime;

            if ( result.tickCount==0 && tradingMarketInfo.getTradingTime()>3600*1000 ) {
                continue;
            }
            result.tickCount++; //只计算正式开市的数据
            result.exchangeable = marketData.instrumentId;
        }
        //对于开始结束时间一分钟内没有行情的数据, 丢弃
        for(MarketType marketType:mdBegineEndTimes.keySet()) {
            LocalDateTime[] marketBeginEndTime = mdBegineEndTimes.get(marketType);
            LocalDateTime mdOpenTime = marketBeginEndTime[0];
            LocalDateTime mdCloseTime = marketBeginEndTime[1];
            LocalDateTime mdFirstTime = marketBeginEndTime[2];
            LocalDateTime mdEndTime = marketBeginEndTime[3];
            long mdOpenSeconds = DateUtil.localdatetime2long(result.exchangeable.exchange().getZoneId(), mdOpenTime);
            long mdCloseSeconds = DateUtil.localdatetime2long(result.exchangeable.exchange().getZoneId(), mdCloseTime);
            long mdFirstSeconds = DateUtil.localdatetime2long(result.exchangeable.exchange().getZoneId(), mdFirstTime);
            long mdEndSeconds = DateUtil.localdatetime2long(result.exchangeable.exchange().getZoneId(), mdEndTime);

            if ( Math.abs(mdOpenSeconds-mdFirstSeconds)>60*1000 || Math.abs(mdCloseSeconds-mdEndSeconds)>5*60*1000  ) {
                return null;
            }
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

}
