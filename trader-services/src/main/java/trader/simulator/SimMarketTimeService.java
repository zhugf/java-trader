package trader.simulator;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import trader.common.util.DateUtil;
import trader.service.trade.MarketTimeService;

/**
 * 模拟市场时间驱动
 */
public class SimMarketTimeService implements MarketTimeService {

    private ZoneId timeZone = DateUtil.getDefaultZoneId();
    private LocalDateTime time = LocalDateTime.now();
    private List<SimMarketTimeAware> timeListeners = new ArrayList<>();

    private LocalDate tradingDay;
    private LocalDateTime[][] timeRanges;
    private int timeRangeIndex;

    /**
     * 最小时间间隔(ms)
     */
    private int minTimeInterval = 100;

    @Override
    public long currentTimeMillis() {
        Instant instant = time.atZone(timeZone).toInstant();
        return instant.toEpochMilli();
    }

    @Override
    public LocalDateTime getMarketTime() {
        return time;
    }

    @Override
    public LocalDate getTradingDay() {
        return tradingDay;
    }

    public void addListener(SimMarketTimeAware timeAware) {
        timeListeners.add(timeAware);
    }

    public void setTimeRanges(LocalDate tradingDay, LocalDateTime[][] timeRanges) {
        this.tradingDay = tradingDay;
        this.timeRanges = timeRanges;
        this.time = timeRanges[0][0];
    }

    /**
     * 模拟走动一个时间片
     */
    public boolean nextTimePiece()
    {
        if ( timeRangeIndex>=timeRanges.length) {
            return false;
        }
        LocalDateTime[] timeRange = timeRanges[timeRangeIndex];
        LocalDateTime beginTime = timeRange[0], endTime = timeRange[1];
        if ( time==null ) {
            time = beginTime;
        } else if ( time.compareTo(endTime)>=0 ) {
            timeRangeIndex++;
            return nextTimePiece();
        }
        LocalDateTime dt = time;
        for(SimMarketTimeAware c:timeListeners)
            c.onTimeChanged(tradingDay, dt);
        time = time.plus(minTimeInterval, ChronoUnit.MILLIS);
        return true;
    }

}
