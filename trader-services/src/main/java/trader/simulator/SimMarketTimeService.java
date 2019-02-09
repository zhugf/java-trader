package trader.simulator;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
    private LocalDateTime beginTime;
    private LocalDateTime endTime;

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
    public LocalDate getMarketDay() {
        return time.toLocalDate();
    }

    public void addListener(SimMarketTimeAware timeAware) {
        timeListeners.add(timeAware);
    }

    public void setTimeRange(LocalDate tradingDay, LocalDateTime beginTime, LocalDateTime endTime) {
        this.tradingDay = tradingDay;
        this.beginTime = beginTime;
        this.endTime = endTime;
        this.time = beginTime;
    }

    /**
     * 模拟走动一个时间片
     */
    public boolean nextTimePiece()
    {
        //提前10分钟
        if ( time==null )
            time = beginTime;
        //收市一分钟后结束
        if ( time.compareTo(endTime)>=0 )
            return false;

        LocalDateTime dt = time;
        for(SimMarketTimeAware c:timeListeners)
            c.onTimeChanged(tradingDay, dt);
        time = time.plusNanos(minTimeInterval*1000000);
        return true;
    }

}
