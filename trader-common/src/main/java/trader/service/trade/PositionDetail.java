package trader.service.trade;

import java.time.LocalDateTime;

import trader.service.trade.TradeConstants.PosDirection;

/**
 * 持仓明细
 */
public interface PositionDetail {

    public PosDirection getDirection();

    public int getVolume();

    public long getPrice();

    public LocalDateTime getOpenTime();

    public boolean isToday();
}
