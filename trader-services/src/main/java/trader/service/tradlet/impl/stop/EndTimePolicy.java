package trader.service.tradlet.impl.stop;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import trader.common.beans.BeansContainer;
import trader.common.util.DateUtil;
import trader.service.tradlet.Playbook;
import trader.service.tradlet.TradletConstants.StopLossPolicy;

/**
 * 指定最后时间止损策略
 */
public class EndTimePolicy extends AbsStopPolicy {

    private LocalDateTime endTime;
    private long endEpochMillis;

    EndTimePolicy(BeansContainer beansContainer, Playbook playbook, JsonElement config) {
        super(beansContainer);

        LocalTime endTime = DateUtil.str2localtime(config.getAsString());
        LocalDateTime currTime = mtService.getMarketTime();
        LocalDateTime endTime0 = endTime.atDate(currTime.toLocalDate());
        if ( endTime0.isBefore(currTime)) { //如果 00:55:05 < 21:00:00, 那么加1天, 应对夜市隔日场景
            endTime0 = endTime0.plusDays(1);
        }
        Instant endInstant = endTime0.atZone(playbook.getExchangable().exchange().getZoneId()).toInstant();
        this.endTime = endTime0;
        this.endEpochMillis = endInstant.toEpochMilli();
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("endTime", DateUtil.date2str(endTime));
        return json;
    }

    @Override
    public String needStop(Playbook playbook, long newPrice) {
        long currentTimeMillis = mtService.currentTimeMillis();
        if ( currentTimeMillis>=endEpochMillis ) {
            return StopLossPolicy.EndTime.name();
        }
        return null;
    }

}
