package trader.service.tradlet.impl.stop;

import java.util.Collections;
import java.util.List;

import com.google.gson.JsonElement;

import trader.common.beans.BeansContainer;
import trader.common.util.JsonUtil;
import trader.common.util.PriceUtil;
import trader.service.md.MarketData;
import trader.service.trade.TradeConstants.PosDirection;
import trader.service.tradlet.Playbook;

/**
 * 价格区间的止盈策略.
 */
public class PriceStepGainPolicy extends AbsStopPolicy {

    private List<PriceStep> steps;

    public PriceStepGainPolicy(BeansContainer beansContainer, Playbook playbook, long openingPrice, List configs) {
        super(beansContainer);

        steps = PriceStep.config2steps(playbook, openingPrice, false, configs);
        //多仓从小到大排序, 空仓从大到小
        boolean reverse = playbook.getDirection()==PosDirection.Short;
        if ( reverse ) {
            Collections.reverse(steps);
        }
    }

    @Override
    public JsonElement toJson() {
        return JsonUtil.object2json(steps);
    }

    @Override
    public String needStop(Playbook playbook, MarketData tick) {
        long currTimeMillis = mtService.currentTimeMillis();
        long lastPrice = tick.lastPrice;
        //计算方法, 计算最后一个没有符合条件的价位, 检查这个价位是否曾经满足过(beginMillis>0)
        PriceStep stopStep = null;

        for(int i=0;i<steps.size();i++) {
            PriceStep step = steps.get(i);
            if ( !step.hasMetBefore() ) {
                if ( !step.getRange() ) { //多仓, 先要高于priceBase
                    if ( step.getPriceBase()<=lastPrice) {
                        step.setMeet(true);
                    }
                }else { //空仓, 先要低于priceBase
                    if ( step.getPriceBase()>=lastPrice) {
                        step.setMeet(true);
                    }
                }
            } else {
                int compareResult = step.compare(tick);
                if ( compareResult==-1 ) {
                    stopStep = step;
                    break;
                }
            }
        }

        String result = null;
        if ( stopStep!=null ) {
            result = StopPolicy.PriceStepGain.name()+(stopStep.getRange()?"+":"-")+PriceUtil.long2str(stopStep.getPriceBase());
        }
        return result;
    }

}
