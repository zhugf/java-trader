package trader.service.md;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;

import trader.common.beans.BeansContainer;
import trader.common.beans.ServiceState;
import trader.common.exchangeable.Exchangeable;
import trader.common.util.FileUtil;
import trader.common.util.IOUtil;
import trader.common.util.StringUtil;
import trader.common.util.TraderHomeUtil;

/**
 * 异步保存行情数据
 */
public class MarketDataSaver {
    private static Logger logger = LoggerFactory.getLogger(MarketDataSaver.class);

    /**
     * 主动刷新间隔(ms)
     */
    private static final int FLUSH_INTERVAL = 15*1000;

    /**
     * 刷新检查间隔(ms)
     */
    private static final int FLUSH_CHECK_INTERVAL = 5*1000;

    private static class WriterInfo implements AutoCloseable {

        private final String key;

        private Writer writer;
        /**
         * 上传刷新时间
         */
        private long flushTime;
        /**
         * 刷新时的数据版本
         */
        private int flushVer=0;
        /**
         * 数据版本
         */
        private volatile int dataVer = 0;

        public WriterInfo(String key, BufferedWriter writer) {
            this.key = key;
            this.writer = writer;
            flushTime = System.currentTimeMillis();
        }

        boolean flush(boolean force) throws IOException
        {
            long currTime = System.currentTimeMillis();
            boolean result = false;
            if ( force || (dataVer!=flushVer && (currTime-flushTime)>FLUSH_INTERVAL) ) {
                writer.flush();
                flushVer = dataVer;
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
            dataVer++;
        }

    }

    private LinkedBlockingQueue<MarketData> queue = new LinkedBlockingQueue<>();
    private MarketDataService marketDataService;
    private Map<String, WriterInfo> writerMap = new HashMap<>();
    private File dataDir;
    StringBuilder rowBuf = new StringBuilder(1024);

    public MarketDataSaver(BeansContainer beansContainer){
        this.marketDataService = beansContainer.getBean(MarketDataService.class);
        ExecutorService executorService = beansContainer.getBean(ExecutorService.class);
        dataDir = TraderHomeUtil.getDirectory(TraderHomeUtil.DIR_MARKETDATA);
        dataDir.mkdirs();
        executorService.execute(()->{
            saveThreadLoop();
        });
    }


    public void asyncSave(MarketData md) {
        queue.offer(md);
    }

    private void saveThreadLoop() {
        long flushInvokeTime = System.currentTimeMillis();
        while( marketDataService.getState()!=ServiceState.Stopped ) {
            MarketData marketData = null;
            try{
                marketData = queue.poll(200, TimeUnit.MILLISECONDS);
            }catch(Throwable t) {}
            if ( marketData!=null ) {
                try {
                    WriterInfo writerInfo = getOrCreateWriter(marketData);
                    rowBuf.setLength(0);
                    marketData.toCsvRow(rowBuf);
                    writerInfo.appendLine(rowBuf.toString());
                } catch (Throwable e) {
                    logger.error("Write market data file failed",e);
                }
            }
            if ( (System.currentTimeMillis()-flushInvokeTime)>=FLUSH_CHECK_INTERVAL ) {
                //每秒检查一次, 确保数据不超过15秒后被保存
                flushAllWriters(false);
                flushInvokeTime = System.currentTimeMillis();
            }
        }
        flushAllWriters(true);
    }

    /**
     * 按需刷新, 定时调用
     */
    public synchronized void flushAllWriters(boolean force) {
        for(WriterInfo writerInfo:writerMap.values()){
            try {
                writerInfo.flush(force);
            }catch(Throwable t){
                logger.error("Writer "+writerInfo.key+" flush failed", t);
            }
        }
    }

    private WriterInfo getOrCreateWriter(MarketData marketData) throws IOException
    {
        String producerId = marketData.producerId;
        Exchangeable instrumentId = marketData.instrument;
        String writerKey = marketData.producerId+"-"+instrumentId;
        WriterInfo writerInfo = writerMap.get(writerKey);
        if ( null==writerInfo ){
            File file = new File(dataDir, marketData.tradingDay+"/"+producerId+"/"+instrumentId+".csv");
            File producerDir = file.getParentFile();
            if( !producerDir.exists()) {
                producerDir.mkdirs();
                saveProviderProps(producerDir, producerId);
            }
            writerInfo = new WriterInfo(writerKey, IOUtil.createBufferedWriter(file, StringUtil.UTF8, true) );
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
