package trader.service.tradlet.impl.stop;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import trader.common.beans.BeansContainer;
import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableTradingTimes;
import trader.common.exchangeable.MarketTimeStage;
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

        endTime = DateUtil.str2localdatetime(config.getAsString());
        if ( endTime==null ) {
            Exchangeable e = playbook.getExchangable();
            ExchangeableTradingTimes tradingTimes = e.exchange().getTradingTimes(e, mtService.getTradingDay());
            LocalTime localTime = DateUtil.str2localtime(config.getAsString());
            LocalDateTime currTime = mtService.getMarketTime();
            LocalDateTime endTime0 = localTime.atDate(currTime.toLocalDate());
            if ( endTime0.isBefore(currTime) ) { //如果 00:55:05 < 21:00:00, 那么加1天, 应对夜市隔日场景
                endTime0 = endTime0.plusDays(1);
            }
            //如果依然不在交易时间, 使用交易日的当天
            if ( tradingTimes.getTimeStage(endTime0)!=MarketTimeStage.MarketOpen ) {
                endTime0 = mtService.getTradingDay().atTime(localTime);
            }
            this.endTime = endTime0;
        }
        Instant endInstant = this.endTime.atZone(playbook.getExchangable().exchange().getZoneId()).toInstant();
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
