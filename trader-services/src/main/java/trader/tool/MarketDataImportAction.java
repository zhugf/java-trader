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

/**
 * 行情数据的归档命令.
 * <BR>行情数据的临时保存的目录结构: TraderHome/marketData/20181010/mdProducerId/shfe.ru1901.csv
 */
public class MarketDataImportAction implements CmdAction {

    private static class MarketDataInfo implements Comparable<MarketDataInfo>{
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

    @Override
    public String getCommand() {
        return "marketData.import";
    }

    @Override
    public void usage(PrintWriter writer) {
        writer.println("marketData import");
        writer.println("\t导入市场行情数据");
    }

    @Override
    public int execute(PrintWriter writer, List<String> options) throws Exception
    {
        File marketData = new File(TraderHomeUtil.getTraderHome(), "marketData");
        File dataDir = new File(TraderHomeUtil.getTraderHome(), "data");
        File trashDir = new File(TraderHomeUtil.getTraderHome(), "trash");
        writer.println("从行情数据目录导入: "+marketData.getAbsolutePath());writer.flush();
        ExchangeableData exchangeableData = new ExchangeableData(dataDir, false);
        for(File dailyDir: FileUtil.listSubDirs(marketData)) {
            LocalDate date = DateUtil.str2localdate(dailyDir.getName());
            if ( date==null ) {
                writer.println("忽略目录 "+dailyDir);
                continue;
            }
            writer.print("备份交易日 "+dailyDir.getName()+" :"); writer.flush();
            LinkedHashMap<Exchangeable, List<MarketDataInfo>> marketDataInfos = loadMarketDataInfos(dailyDir);
            List<Exchangeable> exchangeables = new ArrayList<>(marketDataInfos.keySet());
            Collections.sort(exchangeables);
            for(Exchangeable e:exchangeables) {
                //为每个品种找到最合适的文件
                List<MarketDataInfo> mdInfos = marketDataInfos.get(e);
                Collections.sort(mdInfos);
                //实际导入
                MarketDataInfo mdInfo = mdInfos.get(mdInfos.size()-1);
                archiveMarketData(exchangeableData, date, mdInfo);
                writer.print(" "+mdInfo.exchangeable+"("+mdInfo.savedTicks+"/"+mdInfo.tickCount+")"); writer.flush();
            }
            writer.println();
            //将每日目录转移trash目录中
            moveToTrash(trashDir, dailyDir);
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
    private void archiveMarketData(ExchangeableData exchangeableData, LocalDate date, MarketDataInfo mdInfo) throws IOException
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
        if ( exchangeableData.exists(mdInfo.exchangeable, dataInfo, date) ) {
            String csvText = exchangeableData.load(mdInfo.exchangeable, dataInfo, date);
            CSVDataSet csvDataSet = CSVUtil.parse(csvText);
            while(csvDataSet.next()) {
                MarketData marketData = mdProducer.createMarketData(csvMarshallHelper.unmarshall(csvDataSet.getRow()));
                existsTimes.add(marketData.updateTime);
                csvWriter.next().setRow(csvDataSet.getRow());
            }
        }

        CSVDataSet csvDataSet = CSVUtil.parse(FileUtil.read(mdInfo.marketDataFile));
        while(csvDataSet.next()) {
            MarketData marketData = mdProducer.createMarketData(csvMarshallHelper.unmarshall(csvDataSet.getRow()));
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
            csvWriter.next().setRow(csvDataSet.getRow());
            mdInfo.savedTicks++;
        }
        exchangeableData.save(mdInfo.exchangeable, dataInfo, date, csvWriter.toString());
    }

    /**
     * 依次加载和检测行情数据信息
     */
    private LinkedHashMap<Exchangeable, List<MarketDataInfo>> loadMarketDataInfos(File dailyDir) throws Exception
    {
        LinkedHashMap<Exchangeable, List<MarketDataInfo>> result = new LinkedHashMap<>();
        for(File producerDir : FileUtil.listSubDirs(dailyDir)) {
            MarketDataProducer.Type producerType = detectProducerType(producerDir);
            for(File csvFile:producerDir.listFiles()) {
                if( !csvFile.getName().endsWith(".csv") ) {
                    continue;
                }
                MarketDataInfo mdInfo = loadMarketDataInfo(csvFile, producerType);
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
                result = ConversionUtil.toEnum(MarketDataProducer.Type.class, typeElem.toString());
            }
        }
        return result;
    }

    private MarketDataInfo loadMarketDataInfo(File csvFile, MarketDataProducer.Type producerType) throws IOException
    {
        MarketDataInfo result = new MarketDataInfo();
        result.producerType = producerType;
        result.marketDataFile = csvFile;

        CSVMarshallHelper csvMarshallHelper = createCSVMarshallHelper(producerType);
        MarketDataProducer mdProducer = createMarketDataProducer(producerType);

        CSVDataSet csvDataSet = CSVUtil.parse(FileUtil.read(csvFile));
        while(csvDataSet.next()) {
            MarketData marketData = mdProducer.createMarketData(csvMarshallHelper.unmarshall(csvDataSet.getRow()));
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
