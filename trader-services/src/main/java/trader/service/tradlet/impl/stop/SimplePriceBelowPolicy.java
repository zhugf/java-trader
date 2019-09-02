package trader.service.tradlet.impl.stop;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import trader.common.beans.BeansContainer;
import trader.common.util.PriceUtil;
import trader.service.md.MarketData;
import trader.service.tradlet.Playbook;

/**
 * 简单价格上触停止策略
 */
public class SimplePriceBelowPolicy extends AbsStopPolicy {

    /**
     * 基础价格, 当TICK到这点, 开始创建barrier,
     */
    private long at;

    SimplePriceBelowPolicy(BeansContainer beansContainer, Playbook playbook, long openingPrice) {
        super(beansContainer);

        at = PBATTR_SIMPLE_PRICE_ABOVE.getPrice(playbook.getAttr(PBATTR_SIMPLE_PRICE_ABOVE.name()));
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("at", PriceUtil.long2str(at));
        return json;
    }

    @Override
    public String needStop(Playbook playbook, MarketData tick) {
        String result = null;
        if ( tick!=null && at >0 && tick.lastPrice>=at ) {
            result = PBACTION_SIMPLE_PRICE_ABOVE+ " "+PriceUtil.long2str(at);
        }
        return result;
    }

}
