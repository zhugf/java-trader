package trader.service.tradlet.impl.stop;

import java.util.List;

import com.google.gson.JsonElement;

import trader.common.beans.BeansContainer;
import trader.common.util.JsonEnabled;
import trader.common.util.JsonUtil;
import trader.common.util.PriceUtil;
import trader.service.tradlet.Playbook;
import trader.service.tradlet.TradletConstants.StopLossPolicy;

/**
 * 价格区间-持续时间的止损策略
 */
public class PriceStepLossPolicy extends AbsStopPolicy implements JsonEnabled {

    private PriceStep[] priceSteps;

    PriceStepLossPolicy(BeansContainer beansContainer, Playbook playbook, long openingPrice, JsonElement priceStepsElem){
        super(beansContainer);
        List<PriceStep> priceSteps = config2steps(playbook, openingPrice, false, priceStepsElem);
        this.priceSteps = priceSteps.toArray(new PriceStep[priceSteps.size()]);
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
                return StopLossPolicy.PriceStepLoss.name()+" "+PriceUtil.long2str(priceStep.priceBase);
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
        return JsonUtil.object2json(priceSteps);
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