package trader.service.trade;

import java.time.LocalDateTime;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import trader.common.util.DateUtil;
import trader.common.util.JsonEnabled;
import trader.common.util.PriceUtil;
import trader.service.trade.TradeConstants.PosDirection;

/**
 *
 * TODO 改为: 持仓明细从成交创建, 永远不会删除, 当天平仓只会将Volume置0. 对于高频成交是否需要特别优化?
 */
public class PositionDetailImpl implements PositionDetail, Comparable<PositionDetailImpl>, JsonEnabled {

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
        return "[PosDetail D:"+direction+" V:"+volume+" P:"+PriceUtil.long2price(price)+" T:"+DateUtil.date2str(openTime)+"]";
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("direciton", direction.name());
        json.addProperty("volume", volume);
        json.addProperty("price", PriceUtil.long2str(price));
        json.addProperty("openTime", DateUtil.date2str(openTime));
        json.addProperty("today", today);
        return json;
    }

}
