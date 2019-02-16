package trader.service.tradlet.impl.stop;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import trader.common.beans.BeansContainer;
import trader.common.util.ConversionUtil;
import trader.common.util.JsonEnabled;
import trader.common.util.PriceUtil;
import trader.service.trade.TradeConstants.PosDirection;
import trader.service.tradlet.Playbook;
import trader.service.tradlet.TradletConstants.StopLossPolicy;

/**
 * 价格区间-持续时间的止损策略
 */
public class PriceStepPolicy extends AbsStopPolicy implements JsonEnabled {

    private static class PriceStep{
        /**
         * 价格区间: true表示高于priceBase, 用于空单; false表示低于priceBase, 用于多单
         */
        boolean priceRange;

        /**
         * 价位
         */
        long priceBase;

        /**
         * 持续时间
         */
        int seconds;

        /**
         * 价位开始Epoch Millis
         */
        long beginMillis;

        /**
         * 价位最后Epoch Millis
         */
        long lastMillis;
    }

    private PriceStep[] priceSteps;

    PriceStepPolicy(BeansContainer beansContainer, Playbook playbook, long openingPrice, JsonElement priceStepsElem){
        super(beansContainer);
        List<PriceStep> priceSteps = new ArrayList<>();
        if ( priceStepsElem instanceof JsonObject ) {
            JsonObject priceStepsJson = (JsonObject)priceStepsElem;
            for(String key:priceStepsJson.keySet()) {
                String val = priceStepsJson.get(key).getAsString();
                PriceStep priceStep = new PriceStep();
                priceStep.priceBase = getPriceBase(playbook, openingPrice, str2price(playbook.getExchangable(), key));
                priceStep.seconds = (int)ConversionUtil.str2seconds(val);
                priceSteps.add(priceStep);
            }
        }else {
            JsonArray priceStepsArray = (JsonArray)priceStepsElem;
            for(int i=0;i<priceStepsArray.size();i++) {
                JsonObject priceStepJson = (JsonObject)priceStepsArray.get(i);
                PriceStep priceStep = new PriceStep();
                priceStep.priceBase = getPriceBase(playbook, openingPrice, str2price(playbook.getExchangable(), priceStepJson.get("priceBase").getAsString()));
                priceStep.seconds = (int)ConversionUtil.str2seconds(priceStepJson.get("duration").getAsString());
                priceSteps.add(priceStep);
            }
        }
        for(PriceStep priceStep:priceSteps) {
            if ( playbook.getDirection()==PosDirection.Long ) {
                priceStep.priceRange = false;
            }else {
                priceStep.priceRange = true;
            }
        }
        //从小到大排序
        Collections.sort(priceSteps, (PriceStep p1, PriceStep p2)->{
            return Long.compare(p1.priceBase, p2.priceBase);
        });
        //如果是开多仓, 需要逆序
        if ( playbook.getDirection()==PosDirection.Long ) {
            Collections.reverse(priceSteps);
        }
    }

    @Override
    public String needStop(Playbook playbook, long newPrice) {
        long currTimeMillis = mtService.currentTimeMillis();
        int clearIndex = -1;
        for(int i=0;i<priceSteps.length;i++) {
            PriceStep priceStep = priceSteps[i];
            if ( priceStep.priceRange ) { //价格>=priceBase
                if ( newPrice>priceStep.priceBase) {
                    extendPriceStep(priceStep, currTimeMillis);
                } else {
                    clearIndex = i;
                    break;
                }
            }else { //价格<=priceBase
                if ( newPrice<priceStep.priceBase ) {
                    extendPriceStep(priceStep, currTimeMillis);
                } else {
                    clearIndex = i;
                    break;
                }
            }
            //检查价格阶梯时间是否达到
            if ( marketTimeGreateThan(playbook.getExchangable(), priceStep.beginMillis, priceStep.lastMillis, priceStep.seconds) ){
                return StopLossPolicy.PriceStep.name()+" "+PriceUtil.long2str(priceStep.priceBase);
            }
        }
        //后面的价格统统清除
        if ( clearIndex>0 ) {
            for(int i=clearIndex; i<priceSteps.length;i++) {
                priceSteps[i].beginMillis = 0;
                priceSteps[i].lastMillis = 0;
            }
        }
        return null;
    }

    @Override
    public JsonElement toJson() {
        JsonArray array = new JsonArray();
        for(int i=0;i<priceSteps.length;i++) {
            PriceStep step = priceSteps[i];
            JsonObject json = new JsonObject();
            json.addProperty("priceRange", step.priceRange);
            json.addProperty("priceBase", step.priceBase);
            json.addProperty("seconds", step.priceBase);
            json.addProperty("beginMillis", step.beginMillis);
            json.addProperty("lastMillis", step.lastMillis);
            array.add(json);
        }
        return array;
    }

    private long getPriceBase(Playbook playbook, long openingPrice, long stopPrice) {
        long result = 0;
        if (playbook.getDirection() == PosDirection.Long) {
            result = openingPrice- stopPrice;
        } else {
            result = openingPrice+stopPrice;
        }
        return result;
    }

    /**
     * 延续PriceStep时间
     */
    private void extendPriceStep(PriceStep priceStep, long currTimeMillis) {
        priceStep.lastMillis = currTimeMillis;
        if ( priceStep.beginMillis==0) {
            priceStep.beginMillis = currTimeMillis;
        }
    }

}