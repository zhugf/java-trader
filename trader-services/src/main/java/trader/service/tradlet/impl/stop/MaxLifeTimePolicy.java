package trader.service.tradlet.impl.stop;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import trader.common.beans.BeansContainer;
import trader.common.util.ConversionUtil;
import trader.common.util.JsonEnabled;
import trader.service.tradlet.Playbook;
import trader.service.tradlet.TradletConstants.StopPolicy;

/**
 * 最长生存周期止损策略
 */
public class MaxLifeTimePolicy extends AbsStopPolicy implements JsonEnabled {

    private int maxSeconds;

    MaxLifeTimePolicy(BeansContainer beansContainer, JsonElement config) {
        super(beansContainer);
        maxSeconds = (int)ConversionUtil.str2seconds(config.getAsString());
    }

    @Override
    public String needStop(Playbook playbook, long newPrice) {
        long beginTime = playbook.getStateTuples().get(0).getTimestamp();
        long currTime = mtService.currentTimeMillis();
        if ( marketTimeGreateThan(playbook.getExchangable(), beginTime, currTime, maxSeconds) ){
            return StopPolicy.MaxLifeTime.name();
        }
        return null;
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("maxSeconds", maxSeconds);
        return json;
    }

}