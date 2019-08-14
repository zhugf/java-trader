package trader.service.tradlet.impl.stop;

import java.time.Instant;
import java.time.LocalDateTime;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import trader.common.beans.BeansContainer;
import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableUtil;
import trader.common.exchangeable.MarketType;
import trader.common.util.DateUtil;
import trader.service.md.MarketData;
import trader.service.tradlet.Playbook;

/**
 * 指定最后时间止损策略
 */
public class EndTimePolicy extends AbsStopPolicy {

    private LocalDateTime endTime;
    private long endEpochMillis;

    EndTimePolicy(BeansContainer beansContainer, Playbook playbook, Object config) {
        super(beansContainer);
        Exchangeable e = playbook.getInstrument();
        tradingTimes = e.exchange().getTradingTimes(e, mtService.getTradingDay());
        MarketType seg = tradingTimes.getSegmentType(mtService.getMarketTime());
        endTime = ExchangeableUtil.resolveTime(tradingTimes, seg, config.toString());
        Instant endInstant = this.endTime.atZone(playbook.getInstrument().exchange().getZoneId()).toInstant();
        this.endEpochMillis = endInstant.toEpochMilli();
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("endTime", DateUtil.date2str(endTime));
        return json;
    }

    @Override
    public String needStop(Playbook playbook, MarketData tick) {
        long currentTimeMillis = mtService.currentTimeMillis();
        if ( currentTimeMillis>=endEpochMillis ) {
            return StopPolicy.EndTime.name();
        }
        return null;
    }

}
