package trader.service.tradlet.impl.stop;

import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import trader.common.beans.BeansContainer;
import trader.common.util.PriceUtil;
import trader.service.md.MarketData;
import trader.service.trade.TradeConstants.PosDirection;
import trader.service.tradlet.Playbook;

/**
 * 简单价格停止策略
 */
public class SimpleLossPolicy extends AbsStopPolicy {
    /**
     * 基础价格, 当TICK到这点, 开始创建barrier,
     */
    private long priceBase;

    /**
     * 开多仓时false, 代表低于PriceBase触碰
     */
    private boolean priceRange;

    private long tolerance;

    /**
     * 当价格到达priceBase后, 开始进入barrier的跟踪.
     */
    private PriceStep step;

    SimpleLossPolicy(BeansContainer beansContainer, Playbook playbook, long openingPrice, Object config) {
        super(beansContainer);

        priceRange = playbook.getDirection()!=PosDirection.Long;
        if ( config instanceof Map ) {
            //有base, tolerance的配置
            step = PriceStep.config2step(playbook, openingPrice, false, config);
            tolerance = Math.abs(step.getPriceBase()-step.getPriceEdge());
        } else {
            //只有base
            priceBase = getPriceBase(playbook, openingPrice, config.toString(), false);
        }
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("priceBase", PriceUtil.long2str(priceBase));
        json.addProperty("tolerance", PriceUtil.long2str(tolerance));
        json.addProperty("priceRange", priceRange);
        return json;
    }

    @Override
    public String needStop(Playbook playbook, MarketData tick) {
        boolean stop = false;
        long price = tick.lastPrice;
        if ( tolerance==0 ) {
            stop = needStopSimple(price);
        } else { //带有价格宽容度的止损
            int stepResult = step.compare(tick);
            stop = stepResult==-1;
        }

        String result = null;
        if ( stop ) {
            if ( priceRange) {
                result = StopPolicy.SimpleLoss.name()+"+"+PriceUtil.long2str(price);
            }else {
                result = StopPolicy.SimpleLoss.name()+"-"+PriceUtil.long2str(price);
            }
        }
        return result;
    }

    /**
     * 简单碰价止损
     */
    private boolean needStopSimple(long newPrice) {
        boolean result = false;
        if ( priceRange ) {
            if ( newPrice>=priceBase ) {
                result = true;
            }
        }else {
            if ( newPrice<=priceBase ) {
                result = true;
            }
        }
        return result;
    }

}
