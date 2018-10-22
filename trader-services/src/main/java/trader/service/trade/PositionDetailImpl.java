package trader.service.trade;

import java.time.LocalDateTime;

import trader.common.util.PriceUtil;
import trader.service.trade.TradeConstants.PosDirection;

public class PositionDetailImpl implements PositionDetail, Comparable<PositionDetailImpl> {

    private PosDirection direction;
    private int volume;
    private long price;
    private LocalDateTime openTime;
    private boolean today;

    public PositionDetailImpl(PosDirection direction, int volume, long price, LocalDateTime openDate, boolean today) {
        this.direction = direction;
        this.volume = volume;
        this.price = price;
        this.openTime = openDate;
        this.today = today;
    }

    public PositionDetailImpl(PositionDetailImpl detail, int volume) {
        this(detail.direction, volume, detail.price, detail.openTime, detail.today);
    }

    @Override
    public PosDirection getDirection() {
        return direction;
    }

    @Override
    public int getVolume() {
        return volume;
    }

    @Override
    public long getPrice() {
        return price;
    }

    @Override
    public LocalDateTime getOpenTime() {
        return openTime;
    }

    @Override
    public boolean isToday() {
        return today;
    }

    @Override
    public int compareTo(PositionDetailImpl o) {
        return openTime.compareTo(o.openTime);
    }

    public int addVolume(int toadd) {
        volume+=toadd;
        return volume;
    }

    @Override
    public String toString() {
        return "PosDetail["+direction+" "+volume+" "+PriceUtil.long2price(price)+" "+openTime+"]";
    }
}
