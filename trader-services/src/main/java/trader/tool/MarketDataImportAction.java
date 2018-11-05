package trader.tool;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.ta4j.core.Bar;

import com.google.common.io.Files;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableData;
import trader.common.exchangeable.ExchangeableData.DataInfo;
import trader.common.exchangeable.MarketTimeStage;
import trader.common.util.*;
import trader.common.util.csv.CtpCSVMarshallHelper;
import trader.service.md.MarketData;
import trader.service.md.MarketDataProducer;
import trader.service.md.ctp.CtpMarketDataProducer;
import trader.service.ta.FutureBar;
import trader.service.ta.TimeSeriesLoader;

/**
 * 行情数据的归档命令.
 * <BR>行情数据的临时保存的目录结构: TraderHome/marketData/20181010/mdProducerId/shfe.ru1901.csv
 */
public class MarketDataImportAction implements CmdAction {

    private static class MarketDataInfo implements Comparable<MarketDataInfo>{
        LocalDate tradingDay;
        Exchangeable exchangeable;
        File marketDataFile;
        MarketDataProducer.Type producerType = MarketDataProducer.Type.ctp;
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
                archiveMarketData(date, mdInfo);
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
    private void archiveMarketData(LocalDate date, MarketDataInfo mdInfo) throws IOException
    {
        DataInfo dataInfo = ExchangeableData.TICK_CTP;
        switch(mdInfo.producerType) {
        case ctp:
            dataInfo = ExchangeableData.TICK_CTP;
            break;
        default:
            throw new RuntimeException("不支持的数据类型: "+mdInfo.producerType);
        }
        CSVMarshallHelper csvMarshallHelper = createCSVMarshallHelper(mdInfo.producerType);
        MarketDataProducer mdProducer = createMarketDataProducer(mdInfo.producerType);

        Set<LocalDateTime> existsTimes = new TreeSet<>();
        CSVWriter csvWriter = new CSVWriter<>(csvMarshallHelper);
        //先加载当天已有的TICK数据
        if ( exchangeableData.exists(mdInfo.exchangeable, dataInfo, date) ) {
            String csvText = exchangeableData.load(mdInfo.exchangeable, dataInfo, date);
            CSVDataSet csvDataSet = CSVUtil.parse(csvText);
            while(csvDataSet.next()) {
                MarketData marketData = mdProducer.createMarketData(csvMarshallHelper.unmarshall(csvDataSet.getRow()), mdInfo.tradingDay);
                existsTimes.add(marketData.updateTime);
                csvWriter.next().setRow(csvDataSet.getRow());
            }
        }
        //再写入TICK数据
        CSVDataSet csvDataSet = CSVUtil.parse(FileUtil.read(mdInfo.marketDataFile));
        List<MarketData> savedDatas = new ArrayList<>();
        while(csvDataSet.next()) {
            MarketData marketData = mdProducer.createMarketData(csvMarshallHelper.unmarshall(csvDataSet.getRow()), mdInfo.tradingDay);
            if ( existsTimes.contains(marketData.updateTime)) {
                continue;
            }
            MarketTimeStage timeframe = marketData.instrumentId.getTimeStage(marketData.updateTime);
            int tradingMillis = marketData.instrumentId.getTradingMilliSeconds(marketData.updateTime);
            if ( timeframe!=MarketTimeStage.MarketOpen ) {
                continue;
            }
            if ( csvDataSet.getRowIndex()<=2 && tradingMillis>3600*1000 ) {
                continue;
            }
            savedDatas.add(marketData);
            csvWriter.next().setRow(csvDataSet.getRow());
            mdInfo.savedTicks++;
        }
        exchangeableData.save(mdInfo.exchangeable, dataInfo, date, csvWriter.toString());
        //写入MIN1数据
        saveMin1Bars(date, mdInfo, savedDatas);
    }

    /**
     * 将原始日志统计为MIN1
     */
    private void saveMin1Bars(LocalDate date, MarketDataInfo mdInfo, List<MarketData> marketDatas) throws IOException
    {
        DataInfo dataInfo = ExchangeableData.MIN1;

        List<Bar> bars = TimeSeriesLoader.marketDatas2bars(mdInfo.exchangeable, dataInfo.getLevel(), marketDatas);
        CSVWriter csvWriter = new CSVWriter<>(dataInfo.getColumns());
        //加载已有MIN1
        if ( exchangeableData.exists(mdInfo.exchangeable, dataInfo, date) ) {
            String csvText = exchangeableData.load(mdInfo.exchangeable, dataInfo, date);
            CSVDataSet csvDataSet = CSVUtil.parse(csvText);
            while(csvDataSet.next()) {
                csvWriter.next().setRow(csvDataSet.getRow());
            }
        }
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
            MarketDataProducer.Type producerType = detectProducerType(producerDir);
            for(File csvFile:producerDir.listFiles()) {
                if( !csvFile.getName().endsWith(".csv") ) {
                    continue;
                }
                MarketDataInfo mdInfo = loadMarketDataInfo(tradingDay, csvFile, producerType);
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
    MarketDataProducer.Type detectProducerType(File producerDir) throws IOException
    {
        MarketDataProducer.Type result = MarketDataProducer.Type.ctp;
        File producerJson = new File(producerDir, "producer.json");
        if (producerJson.exists()) {
            JsonObject json = (JsonObject) (new JsonParser()).parse(FileUtil.read(producerJson));
            JsonElement typeElem = json.get("type");
            if ( typeElem!=null ) {
                result = ConversionUtil.toEnum(MarketDataProducer.Type.class, typeElem.getAsString());
            }
        }
        return result;
    }

    private MarketDataInfo loadMarketDataInfo(LocalDate tradingDay, File csvFile, MarketDataProducer.Type producerType) throws IOException
    {
        MarketDataInfo result = new MarketDataInfo();
        result.producerType = producerType;
        result.marketDataFile = csvFile;
        result.tradingDay = tradingDay;

        CSVMarshallHelper csvMarshallHelper = createCSVMarshallHelper(producerType);
        MarketDataProducer mdProducer = createMarketDataProducer(producerType);

        CSVDataSet csvDataSet = CSVUtil.parse(FileUtil.read(csvFile));
        while(csvDataSet.next()) {
            MarketData marketData = mdProducer.createMarketData(csvMarshallHelper.unmarshall(csvDataSet.getRow()), null);
            MarketTimeStage timeframe = marketData.instrumentId.getTimeStage(marketData.updateTime);
            int tradingMillis = marketData.instrumentId.getTradingMilliSeconds(marketData.updateTime);
            if ( timeframe!=MarketTimeStage.MarketOpen ) {
                continue;
            }
            if ( result.tickCount==0 && tradingMillis>3600*1000 ) {
                continue;
            }
            result.tickCount++; //只计算正式开市的数据
            result.exchangeable = marketData.instrumentId;
        }
        return result;
    }

    private CSVMarshallHelper createCSVMarshallHelper(MarketDataProducer.Type producerType) {
        switch(producerType) {
        case ctp:
            return new CtpCSVMarshallHelper();
        default:
            return null;
        }
    }

    private MarketDataProducer createMarketDataProducer(MarketDataProducer.Type producerType) {
        switch(producerType) {
        case ctp:
            return new CtpMarketDataProducer();
        default:
            return null;
        }
    }
}
