package trader.service.tradlet.impl.stop;

import java.time.LocalDateTime;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import trader.common.beans.BeansContainer;
import trader.common.exchangeable.Exchangeable;
import trader.common.util.DateUtil;
import trader.common.util.StringUtil;
import trader.service.md.MarketData;
import trader.service.tradlet.Playbook;

/**
 * 指定最后时间止损策略
 */
public class EndTimePolicy extends AbsStopPolicy {

    private String endTimeConfig;
    private LocalDateTime endTime;

    EndTimePolicy(BeansContainer beansContainer, Playbook playbook) {
        super(beansContainer);
        Exchangeable e = playbook.getInstrument();
        tradingTimes = e.exchange().getTradingTimes(e, mtService.getTradingDay());
        endTime = PBATTR_END_TIME.getDateTime(playbook);
        endTimeConfig = PBATTR_END_TIME.getString(playbook);
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("endTime", DateUtil.date2str(endTime));
        return json;
    }

    public boolean needRebuild(Playbook playbook) {
        String endTimeConfig2 = PBATTR_END_TIME.getString(playbook);

        return !StringUtil.equals(endTimeConfig, endTimeConfig2);
    }

    @Override
    public String needStop(Playbook playbook, MarketData tick) {
        String result = null;
        if ( endTime!=null && endTime.compareTo(mtService.getMarketTime())<=0 ) {
            result = PBACTION_ENDTIME+" "+DateUtil.date2str(endTime);
        }
        return result;
    }

}
