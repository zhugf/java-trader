package trader.service.md;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;
import com.lmax.disruptor.EventHandler;

import trader.common.exchangeable.Exchangeable;
import trader.common.util.FileUtil;
import trader.common.util.IOUtil;
import trader.common.util.StringUtil;
import trader.common.util.TraderHomeUtil;
import trader.service.event.AsyncEvent;

/**
 * 异步保存行情数据
 */
public class MarketDataSaver implements EventHandler<AsyncEvent> {
    private static Logger logger = LoggerFactory.getLogger(MarketDataSaver.class);

    /**
     * 主动刷新间隔(ms)
     */
    private static final int FLUSH_INTERVAL = 15*1000;

    private static class WriterInfo implements AutoCloseable {
        private Writer writer;
        /**
         * 上传刷新时间
         */
        private long flushTime;
        /**
         * 已经写数据, 需要刷新
         */
        private boolean needFlush;

        public WriterInfo(BufferedWriter writer) {
            this.writer = writer;
            flushTime = System.currentTimeMillis();
        }

        boolean flush() throws IOException
        {
            long currTime = System.currentTimeMillis();
            boolean result = false;
            if ( needFlush && (currTime-flushTime)>FLUSH_INTERVAL ) {
                writer.flush();
                needFlush = false;
                flushTime = currTime;
                result = true;
            }
            return result;
        }

        @Override
        public void close() throws Exception {
            writer.flush();
            writer.close();
        }

        public void appendLine(String string) throws IOException {
            writer.write(string);
            writer.write("\n");
            needFlush = true;
        }

    }
    private MarketDataService marketDataService;
    private Map<String, WriterInfo> writerMap = new HashMap<>();
    private File dataDir;
    StringBuilder rowBuf = new StringBuilder(1024);

    public MarketDataSaver(MarketDataService marketDataService){
        this.marketDataService = marketDataService;
        dataDir = TraderHomeUtil.getDirectory(TraderHomeUtil.DIR_MARKETDATA);
        dataDir.mkdirs();
    }

    @Override
    public void onEvent(AsyncEvent event, long sequence, boolean endOfBatch) throws Exception {
        if ( event.eventType!=AsyncEvent.EVENT_TYPE_MARKETDATA ) {
            return;
        }
        MarketData marketData = (MarketData)event.data;
        try {
            WriterInfo writerInfo = getOrCreateWriter(marketData);
            rowBuf.setLength(0);
            marketData.toCsvRow(rowBuf);
            writerInfo.appendLine(rowBuf.toString());
        } catch (Throwable e) {
            logger.error("Write market data file failed",e);
        }
    }

    /**
     * 按需刷新. 需要被定时调用
     */
    public void flushAllWriters() {
        for(Iterator<Map.Entry<String, WriterInfo>> it=writerMap.entrySet().iterator(); it.hasNext();){
            Map.Entry<String, WriterInfo> writerInfoEntry = it.next();
            String key = writerInfoEntry.getKey();
            WriterInfo writerInfo = writerInfoEntry.getValue();
            try {
                writerInfo.flush();
            }catch(Throwable t){
                logger.error("Writer "+key+" flush failed", t);
                it.remove();
            }
        }
    }

    private WriterInfo getOrCreateWriter(MarketData marketData) throws IOException
    {
        String producerId = marketData.producerId;
        Exchangeable instrumentId = marketData.instrumentId;
        String writerKey = marketData.producerId+"-"+instrumentId.id();
        WriterInfo writerInfo = writerMap.get(writerKey);
        if ( null==writerInfo ){
            File file = new File(dataDir, marketData.tradingDay+"/"+producerId+"/"+instrumentId+".csv");
            File producerDir = file.getParentFile();
            if( !producerDir.exists()) {
                producerDir.mkdirs();
                saveProviderProps(producerDir, producerId);
            }
            writerInfo = new WriterInfo( IOUtil.createBufferedWriter(file, StringUtil.UTF8, true) );
            if ( file.length()==0 ){
                writerInfo.writer.write(marketData.getCsvHead());
                writerInfo.writer.write("\n");
            }
            writerMap.put(writerKey, writerInfo);
        }
        return writerInfo;
    }

    /**
     * 为每个producer目录保存一个标准 producer.json文件
     */
    private void saveProviderProps(File mdProviderDir, String producerId)
    {
        String producerType = "ctp";
        MarketDataProducer mdProducer = marketDataService.getProducer(producerId);
        if ( mdProducer!=null ) {
            producerType = mdProducer.getProvider();
        }
        JsonObject json =new JsonObject();
        json.addProperty("id", producerId);
        json.addProperty("provider", producerType);
        try{
            FileUtil.save(new File(mdProviderDir,"producer.json"), json.toString());
        }catch(Throwable t) {}
    }

}
