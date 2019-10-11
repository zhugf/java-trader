package trader.service.tradlet.impl.stop;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import trader.common.beans.BeansContainer;
import trader.common.util.PriceUtil;
import trader.common.util.StringUtil;
import trader.service.md.MarketData;
import trader.service.tradlet.Playbook;

public class BarrieredPriceDownPolicy extends AbsStopPolicy {

    private String config;
    private long barrier = 0;
    private long step;
    private boolean touchBarrier;
    private long maxPrice;

    BarrieredPriceDownPolicy(BeansContainer beansContainer, Playbook playbook) {
        super(beansContainer);

        config = PBATTR_BARRIERED_PRICE_DOWN.getString(playbook);
        if ( !StringUtil.isEmpty(config)) {
            for(String[] kv:StringUtil.splitKVs(config)) {
                if ( kv.length<2 ) {
                    continue;
                }
                String k=kv[0], v=kv[1];
                if ("barrier".equalsIgnoreCase(k)) {
                    barrier = PriceUtil.str2long(v);
                } else if ("step".equalsIgnoreCase(k)) {
                    step = PriceUtil.str2long(v);
                }
            }
        }
    }

    public static boolean needPolicy(Playbook playbook) {
        String config = PBATTR_BARRIERED_PRICE_DOWN.getString(playbook);
        return !StringUtil.isEmpty(config);
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();

        json.addProperty("barrier", PriceUtil.long2str(barrier));
        json.addProperty("step", PriceUtil.long2str(step));

        json.addProperty("touchBarrier", touchBarrier);
        json.addProperty("maxPrice", PriceUtil.long2str(maxPrice));

        return json;
    }

    public boolean needRebuild(Playbook playbook) {
        String config2 = PBATTR_BARRIERED_PRICE_DOWN.getString(playbook);

        return !StringUtil.equals(config, config2);
    }

    @Override
    public String needStop(Playbook playbook, MarketData tick) {
        String result = null;
        if ( !touchBarrier) {
            if ( barrier!= 0 ) {
                if ( tick.lastPrice>=barrier) {
                    touchBarrier = true;
                    maxPrice = tick.lastPrice;
                }
            }
        } else {
            if ( tick.lastPrice>maxPrice ) {
                maxPrice = tick.lastPrice;
            } else {
                if ( (maxPrice-step)>=tick.lastPrice ) {
                    result = PBACTION_BARRIERED_PRICE_DOWN+" "+PriceUtil.long2str(maxPrice);
                }
            }
        }
        return result;
    }

}
