package trader.simulator;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 所有对模拟时间敏感的服务都需要实现的接口
 */
public interface SimMarketTimeAware {

    public void onTimeChanged(LocalDate tradingDay, LocalDateTime actionTime, long timestamp);

}
