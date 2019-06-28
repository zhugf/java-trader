package trader.service.tradlet.impl.stop;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import trader.common.beans.BeansContainer;
import trader.common.util.JsonEnabled;
import trader.service.md.MarketData;
import trader.service.tradlet.Playbook;

/**
 * 价格趋势反转止损策略
 */
public class PriceTrendLossPolicy extends AbsStopPolicy implements JsonEnabled{

    PriceTrendLossPolicy(BeansContainer beansContainer, Playbook playbook, long openingPrice, Object config) {
        super(beansContainer);
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();

        return json;
    }

    @Override
    public String needStop(Playbook playbook, MarketData tick) {
        return null;
    }

}
