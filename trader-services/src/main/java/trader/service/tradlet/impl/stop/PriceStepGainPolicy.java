package trader.service.tradlet.impl.stop;

import java.util.List;

import com.google.gson.JsonElement;

import trader.common.beans.BeansContainer;
import trader.common.util.JsonUtil;
import trader.common.util.PriceUtil;
import trader.service.tradlet.Playbook;

/**
 * 价格区间的止盈策略.
 * TODO 改变算法, 使用TripBarrier
 */
public class PriceStepGainPolicy extends AbsStopPolicy {

    private List<PriceStep> priceSteps;

    PriceStepGainPolicy(BeansContainer beansContainer, Playbook playbook, long openingPrice, JsonElement priceStepsElem) {
        super(beansContainer);
        priceSteps = config2steps(playbook, openingPrice, true, priceStepsElem);
    }

    @Override
    public JsonElement toJson() {
        return JsonUtil.object2json(priceSteps);
    }

    @Override
    public String needStop(Playbook playbook, long newPrice) {
        long currTimeMillis = mtService.currentTimeMillis();
        //计算方法, 计算最后一个没有符合条件的价位, 检查这个价位是否曾经满足过(beginMillis>0)
        int lastMeetIdx = -1;
        for(int i=0;i<priceSteps.size();i++) {
            PriceStep priceStep = priceSteps.get(i);
            boolean meetStep = false;
            if ( priceStep.priceRange ) { //价格>=priceBase
                if ( newPrice>=priceStep.priceBase) {
                    meetStep = true;
                }
            } else {
                if ( newPrice<=priceStep.priceBase) {
                    meetStep = true;
                }
            }
            if ( meetStep ) {
                priceStep.meet = true;
                lastMeetIdx = i;
            } else {
                if ( priceStep.meet ) {
                    if ( priceStep.beginMillis==0 ) {
                        priceStep.beginMillis = currTimeMillis;
                    }
                    priceStep.lastMillis = currTimeMillis;
                }
            }
        }
        int firstNotMeetIdx = lastMeetIdx+1;
        String result = null;
        if ( firstNotMeetIdx<=priceSteps.size()) {
            PriceStep step = priceSteps.get(firstNotMeetIdx);
            if ( step.meet && step.beginMillis>0 && (step.lastMillis-step.beginMillis)>=step.seconds*1000 ) {
                result = StopLossPolicy.PriceStepGain.name()+" "+PriceUtil.long2str(step.priceBase);
            }
        }
        return result;
    }

}
