package trader.common.util;

import java.time.format.DateTimeFormatter;

import trader.service.trade.MarketTimeService;

/**
 * 基于时间戳的序列号生成
 */
public class TimestampSeqGen {
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static String lastTimestamp;
    private MarketTimeService mtService;

    public TimestampSeqGen(MarketTimeService mtService) {
        this.mtService = mtService;
    }

    public synchronized String nextSeq() {
        String timestamp = (mtService.getMarketTime()).format(TIMESTAMP_FORMATTER)+"00";
        if ( lastTimestamp!=null && timestamp.compareTo(lastTimestamp)<=0 ){
            long l = Long.parseLong(lastTimestamp);
            l++;
            timestamp = ""+(l);
        }
        lastTimestamp = timestamp;
        return timestamp;
    }

}
