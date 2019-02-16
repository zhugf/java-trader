package trader.service.tradlet.impl.stop;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import trader.common.beans.BeansContainer;
import trader.common.util.JsonEnabled;
import trader.service.tradlet.Playbook;

/**
 * 价格趋势反转止损策略
 */
public class PriceTrendPolicy extends AbsStopPolicy implements JsonEnabled{
    /**
     * 最大反向笔划长度
     */
    private long maxReverseStrokeLength;

    /**
     * 最大反响线段长度
     */
    private long maxReverseSectionLength;

    /**
     * 最大盈利价格回撤
     */
    private long maxProfitPriceLoss;

    PriceTrendPolicy(BeansContainer beansContainer, Playbook playbook, long openingPrice, JsonElement config) {
        super(beansContainer);
        JsonObject json = (JsonObject)config;

        if ( json.has("maxReverseStrokeLength")) {
            maxReverseStrokeLength = str2price(playbook.getExchangable(), json.get("maxReverseStrokeLength").getAsString());
        }
        if ( json.has("maxReverseSectionLength")) {
            maxReverseStrokeLength = str2price(playbook.getExchangable(), json.get("maxReverseSectionLength").getAsString());
        }
        if ( json.has("maxProfitPriceLoss")) {
            maxReverseStrokeLength = str2price(playbook.getExchangable(), json.get("maxProfitPriceLoss").getAsString());
        }
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();

        return json;
    }

    @Override
    public String needStop(Playbook playbook, long newPrice) {
        return null;
    }

}
