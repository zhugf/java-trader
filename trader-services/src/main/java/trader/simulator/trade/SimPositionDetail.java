package trader.simulator.trade;

import java.time.LocalDateTime;

import trader.service.trade.TradeConstants.PosDirection;

/**
 * 模拟持仓明细
 */
public class SimPositionDetail {
    private PosDirection direction;
    private int volume;
    private long openPrice;
    private LocalDateTime openTime;

    public SimPositionDetail(PosDirection direction, int volume, long openPrice, LocalDateTime openTime) {
        this.direction = direction;
        this.volume = volume;
        this.openPrice = openPrice;
        this.openTime = openTime;
    }

    public PosDirection getDirection() {
        return direction;
    }

    public int getVolume() {
        return volume;
    }

    public long getOpenPrice() {
        return openPrice;
    }

    public LocalDateTime getOpenTime() {
        return openTime;
    }

    public void setVolume(int volume) {
        this.volume = volume;
    }

}
