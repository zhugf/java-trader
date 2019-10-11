package trader.service.tradlet.impl.stop;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import trader.common.beans.BeansContainer;
import trader.common.util.PriceUtil;
import trader.common.util.StringUtil;
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

    SimplePriceBelowPolicy(BeansContainer beansContainer, Playbook playbook) {
        super(beansContainer);

        at = PBATTR_SIMPLE_PRICE_BELOW.getPrice(playbook);
    }

    public static boolean needPolicy(Playbook playbook) {
        String config = PBATTR_SIMPLE_PRICE_BELOW.getString(playbook);
        return !StringUtil.isEmpty(config);
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("at", PriceUtil.long2str(at));
        return json;
    }

    public boolean needRebuild(Playbook playbook) {
        long at2 = PBATTR_SIMPLE_PRICE_BELOW.getPrice(playbook);
        return at!=at2;
    }

    @Override
    public String needStop(Playbook playbook, MarketData tick) {
        String result = null;
        if ( tick!=null && at>0 && tick.lastPrice<=at ) {
            result = PBACTION_SIMPLE_PRICE_BELOW+ " "+PriceUtil.long2str(at);
        }
        return result;
    }

}
