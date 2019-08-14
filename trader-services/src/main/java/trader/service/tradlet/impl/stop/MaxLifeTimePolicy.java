package trader.service.tradlet.impl.stop;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import trader.common.beans.BeansContainer;
import trader.common.util.ConversionUtil;
import trader.common.util.JsonEnabled;
import trader.service.md.MarketData;
import trader.service.tradlet.Playbook;

/**
 * 最长生存周期止损策略
 */
public class MaxLifeTimePolicy extends AbsStopPolicy implements JsonEnabled {

    private int maxTime;

    MaxLifeTimePolicy(BeansContainer beansContainer, Object config) {
        super(beansContainer);
        maxTime = (int)ConversionUtil.str2seconds(config.toString())*1000;
    }

    @Override
    public String needStop(Playbook playbook, MarketData tick) {
        long beginTime = playbook.getStateTuples().get(0).getTimestamp();
        long currTime = mtService.currentTimeMillis();
        if ( marketTimeGreateThan(playbook.getInstrument(), beginTime, currTime, maxTime) ){
            return StopPolicy.MaxLifeTime.name();
        }
        return null;
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("maxTime", maxTime);
        return json;
    }

}