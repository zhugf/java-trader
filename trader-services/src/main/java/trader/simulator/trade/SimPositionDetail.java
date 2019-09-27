package trader.simulator.trade;

import java.time.LocalDateTime;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import trader.common.util.ConversionUtil;
import trader.common.util.DateUtil;
import trader.common.util.JsonEnabled;
import trader.common.util.PriceUtil;
import trader.service.trade.TradeConstants.PosDirection;

/**
 * 模拟持仓明细
 */
public class SimPositionDetail implements JsonEnabled {
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

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("direction", direction.name());
        json.addProperty("volume", volume);
        json.addProperty("openPrice", PriceUtil.long2str(openPrice));
        json.addProperty("openTime", DateUtil.date2str(openTime));
        return json;
    }

    public static SimPositionDetail fromJson(JsonElement json0) {
        JsonObject json = json0.getAsJsonObject();

        SimPositionDetail result = new SimPositionDetail(
                    ConversionUtil.toEnum(PosDirection.class, json.get("direction").getAsString())
                    , json.get("volume").getAsInt()
                    , PriceUtil.str2long(json.get("openPrice").getAsString())
                    , DateUtil.str2localdatetime(json.get("openTime").getAsString())
                );
        return result;
    }
}
