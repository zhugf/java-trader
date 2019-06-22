package trader.service.tradlet.impl.stop;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import trader.common.beans.BeansContainer;
import trader.common.util.PriceUtil;
import trader.service.trade.TradeConstants.PosDirection;
import trader.service.tradlet.Playbook;
import trader.service.tradlet.TradletConstants.StopLossPolicy;

/**
 * 简单价格停止策略
 */
public class SimpleLossPolicy extends AbsStopPolicy {
    /**
     * 开多仓时false, 代表低于PriceBase触碰
     */
    private boolean priceRange;
    /**
     * 开空仓是true, 代表高于PriceBase触碰
     */
    private long priceBase;

    SimpleLossPolicy(BeansContainer beansContainer, Playbook playbook, long openingPrice, JsonElement config) {
        super(beansContainer);
        priceBase = getPriceBase(playbook, openingPrice, config.getAsString(), false);
        priceRange = playbook.getDirection()==PosDirection.Long;
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("priceBase", PriceUtil.long2str(priceBase));
        json.addProperty("priceRange", priceRange);
        return json;
    }

    @Override
    public String needStop(Playbook playbook, long newPrice) {
        String result = null;
        if ( priceRange ) {
            if ( newPrice>=priceBase ) {
                result = StopLossPolicy.PriceStepLoss.name()+"+"+PriceUtil.price2str(newPrice);
            }
        }else {
            if ( newPrice<=priceBase ) {
                result = StopLossPolicy.PriceStepLoss.name()+"-"+PriceUtil.price2str(newPrice);
            }
        }
        return result;
    }

}
