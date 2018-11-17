package trader.simulator;

import java.time.LocalDate;
import java.time.LocalDateTime;

public interface SimMarketTimeAware {

    public void onTimeChanged(LocalDate tradingDay, LocalDateTime actionTime);

}
