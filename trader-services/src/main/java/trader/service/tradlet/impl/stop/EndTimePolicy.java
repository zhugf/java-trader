package trader.service.tradlet.impl.stop;

import java.time.LocalDateTime;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import trader.common.beans.BeansContainer;
import trader.common.exchangeable.Exchangeable;
import trader.common.util.DateUtil;
import trader.service.md.MarketData;
import trader.service.tradlet.Playbook;

/**
 * 指定最后时间止损策略
 */
public class EndTimePolicy extends AbsStopPolicy {

    private LocalDateTime endTime;

    EndTimePolicy(BeansContainer beansContainer, Playbook playbook) {
        super(beansContainer);
        Exchangeable e = playbook.getInstrument();
        tradingTimes = e.exchange().getTradingTimes(e, mtService.getTradingDay());
        endTime = PBATTR_END_TIME.getDateTime(playbook.getAttr(PBATTR_END_TIME.name()));
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("endTime", DateUtil.date2str(endTime));
        return json;
    }

    @Override
    public String needStop(Playbook playbook, MarketData tick) {
        String result = null;
        if ( endTime!=null && endTime.isAfter(mtService.getMarketTime()) ) {
            result = PBACTION_ENDTIME+" "+DateUtil.date2str(endTime);
        }
        return result;
    }

}
