package trader.service.md;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import trader.common.beans.BeansContainer;
import trader.common.beans.Lifecycle;
import trader.common.exchangeable.Exchangeable;
import trader.common.util.*;

/**
 * 异步保存行情数据
 */
public class MarketDataSaver implements Lifecycle, MarketDataListener {
    private static Logger logger = LoggerFactory.getLogger(MarketDataSaver.class);

    /**
     * 主动刷新间隔(ms)
     */
    private static final int FLUSH_INTERVAL = 60*1000;

    private static class WriterInfo{
        Writer writer;
        /**
         * 上传刷新时间
         */
        long flushTime;
    }
    private ExecutorService executorService;
    private TransferQueue<MarketData> marketDataQueue = new LinkedTransferQueue<>();
    private Map<Exchangeable, WriterInfo> writerMap = new HashMap<>();
    private File dataDir;
    private boolean saveThreadStarted;
    private volatile boolean stop;

    public MarketDataSaver(){
    }

    @Override
    public void init(BeansContainer beansContainer) throws Exception
    {
        executorService = beansContainer.getBean(ExecutorService.class);
        dataDir = new File(TraderHomeUtil.getTraderHome(), "marketData");
        dataDir.mkdirs();
        executorService.execute(() -> {
            saveThreadFunc();
        });
    }

    @Override
    public void destory() {
        stop = true;
    }

    @Override
    public void onMarketData(MarketData marketData){
        marketDataQueue.offer(marketData);
    }

    public void saveThreadFunc()
    {
        saveThreadStarted = true;
        MarketData marketData = null;
        StringBuilder rowBuf = new StringBuilder(1024);
        while( true ){
            try {
                marketData=marketDataQueue.poll(1,TimeUnit.SECONDS);
            } catch (InterruptedException e) {}
            if ( null==marketData) {
                if ( stop ) {
                    break;
                }
                continue;
            }
            try {
                WriterInfo writerInfo = getOrCreateWriter(marketData);
                Writer writer = writerInfo.writer;
                rowBuf.setLength(0);
                marketData.toCsvRow(rowBuf);
                writer.write(rowBuf.toString());
                writer.write("\n");
                long currTime = System.currentTimeMillis();
                if ( (currTime-writerInfo.flushTime)>FLUSH_INTERVAL) {
                    writer.flush();
                    writerInfo.flushTime = currTime;
                }
            } catch (Throwable e) {
                logger.error("Write market data file failed",e);
            }
        }
        for( WriterInfo writerInfo:writerMap.values()){
            try {
                writerInfo.writer.flush();
                writerInfo.writer.close();
            }catch(Throwable e){}
        }
        writerMap.clear();
        saveThreadStarted = false;
    }

    private WriterInfo getOrCreateWriter(MarketData marketData) throws IOException
    {
        String producerId = marketData.producerId;
        Exchangeable instrumentId = marketData.instrumentId;
        WriterInfo writerInfo = writerMap.get(instrumentId);
        if ( null==writerInfo ){
            LocalDateTime dateTime = DateUtil.long2datetime(instrumentId.exchange().getZoneId(), marketData.updateTime);
            File file = new File(dataDir, DateUtil.date2str(dateTime.toLocalDate())+"/"+producerId+"/"+instrumentId+".csv");
            file.getParentFile().mkdirs();
            writerInfo = new WriterInfo();
            writerInfo.writer = IOUtil.createBufferedWriter(file, StringUtil.UTF8, true);
            writerInfo.flushTime = System.currentTimeMillis();
            if ( file.length()==0 ){
                writerInfo.writer.write(marketData.getCsvHead());
            }
            writerMap.put(instrumentId, writerInfo);
        }
        return writerInfo;
    }

}
