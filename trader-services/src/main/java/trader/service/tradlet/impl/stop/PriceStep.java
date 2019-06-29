package trader.service.tradlet.impl.stop;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import trader.common.util.ConversionUtil;
import trader.common.util.JsonEnabled;
import trader.common.util.PriceUtil;
import trader.service.md.MarketData;
import trader.service.ta.TripBarrierDef;
import trader.service.ta.TripTickBarrier;
import trader.service.ta.TripTickBarrier.Barrier;
import trader.service.trade.TradeConstants.PosDirection;
import trader.service.tradlet.Playbook;
import trader.service.tradlet.TradletConstants;

/**
 * <pre>
 * ---barrier top-------(priceBase()
 * |<-- seconds     -->
 * |-toerlance height
 * |--barrier bottom----
 * </pre>
 */
public class PriceStep implements JsonEnabled, TradletConstants {
    /**
     * 价位
     */
    private long priceBase;

    /**
     * 价格区间: false表示低于priceBase(高于priceBase正常, 可用于多仓止损), true表示高于priceBase(可用于空仓止损)
     */
    private boolean range;

    /**
     * 最边界的容忍价格
     */
    private long priceEdge;

    /**
     * 最长持续时间(ms)
     */
    private int maxTime;

    /**
     * 是否已经达到这一价格阶梯
     */
    private boolean meet;

    private TripTickBarrier priceBarrier;

    public boolean getRange() {
        return range;
    }

    public long getPriceBase() {
        return priceBase;
    }

    public long getPriceEdge() {
        return priceEdge;
    }

    /**
     * 返回价格区间:
     *
     * @return <LI>1 价格不在关注范围: range==false && price>priceBase
     *         <LI>0 价格在barrier范围: range==false (priceTolerance)<=price<=priceBase
     *         <LI>-1 价格已低于barrier范围: range==false price<=priceTolerance
     */
    public int compare(MarketData tick) {
        long price = tick.lastPrice;
        int result = 0;

        if ( priceBarrier==null ) {
            if ( !range ) {
                //priceEdge ~~~> priceBase
                if ( price>=priceBase ) {
                    meet = true;
                    result = 1;
                } else {
                    if ( price>priceEdge && price<=priceBase ) {
                        result = 0;
                    } else {
                        // price <= priceEdge
                        result = -1;
                    }
                }
            } else {
                //priceBase ~~~> priceEdge
                if ( price<=priceBase ) {
                    meet = true;
                    result = 1;
                } else {
                    if ( price>=priceBase && price<=priceEdge ) {
                        result = 0;
                    } else {
                        //result >= priceEdge
                        result = -1;
                    }
                }
            }
            if ( result==0 ) {
                if ( priceBarrier==null ) {
                    priceBarrier = new TripTickBarrier(Math.max(priceBase, priceEdge), Math.min(priceBase, priceEdge), maxTime, tick);
                    priceBarrier.getBarrier();
                }
            }
        } else {
            Barrier barrier = priceBarrier.update(tick);
            if ( barrier!=null ) {
                switch(barrier) {
                case End:
                    result=-1; //如果一直低于PriceBase, 药丸
                    break;
                case Bottom:
                    if (!range) { //priceEdge ~~~> priceBase
                        result = -1;
                    }else {
                        result = 1;
                    }
                    break;
                case Top:
                    if ( range ) { //priceBase ~~~> priceEdge
                        result = -1;
                    }else {
                        result = 1;
                    }
                }
            }
            if ( result==1 ) {
                priceBarrier = null;
            }
        }

        return result;
    }

    public void setMeet(boolean v) {
        meet = v;
    }

    public boolean hasMetBefore() {
        return meet;
    }

    public boolean isInBarrier() {
        return priceBarrier!=null;
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("range", range);
        json.addProperty("priceBase", priceBase);
        json.addProperty("priceEdge", priceEdge);
        json.addProperty("maxTime", maxTime);
        json.addProperty("meet", meet);
        return json;
    }


    public static PriceStep config2step(Playbook playbook, long openingPrice, boolean follow, Object config) {
        long priceTick = playbook.getExchangable().getPriceTick();
        PriceStep priceStep = new PriceStep();
        if ( playbook.getDirection()==PosDirection.Long ) {
            priceStep.range = follow;
        }else {
            priceStep.range = !follow;
        }

        long tolerance = 0;
        if ( config instanceof Map ) {
            Map<String, Object> map = (Map<String, Object>)config;
            if ( map.containsKey("maxPrice")) {//基于TripBarrierDef的 maxPrice/minPrice/maxTime的设置
                TripBarrierDef barrierDef = TripBarrierDef.fromMap(map);
                priceStep.maxTime = barrierDef.maxTime;
                if ( priceStep.range ) {
                    priceStep.priceBase = Math.min(barrierDef.maxPrice, barrierDef.minPrice);
                    priceStep.priceEdge = Math.max(barrierDef.maxPrice, barrierDef.minPrice);
                } else {
                    priceStep.priceBase = Math.max(barrierDef.maxPrice, barrierDef.minPrice);
                    priceStep.priceEdge = Math.min(barrierDef.maxPrice, barrierDef.minPrice);
                }
            }else {//基于priceBase, duration, tolerance的设置
                priceStep.priceBase = AbsStopPolicy.getPriceBase(playbook, openingPrice, ConversionUtil.toString(map.get("priceBase")), follow);
                priceStep.maxTime =  DEFAULT_PRICE_STEP_TIME;
                if ( map.containsKey("duration")) {
                    priceStep.maxTime = (int)ConversionUtil.str2seconds(ConversionUtil.toString(map.get("duration")))*1000;
                }
                if ( map.containsKey("tolerance") ) {
                    String toleranceStr = ConversionUtil.toString(map.get("tolerance"));
                    if ( toleranceStr.endsWith("%")) { //百分比
                        tolerance = (long)(priceStep.priceBase*ConversionUtil.toDouble(toleranceStr.substring(0, toleranceStr.length()-1))/100);
                    } else if ( toleranceStr.endsWith("t")) { //跳数或直接价格
                        tolerance = PriceUtil.config2long(toleranceStr, priceTick);
                    }
                }
            }
        } else { //只有单一价格, 这时候使用缺省设置
            priceStep.priceBase = AbsStopPolicy.getPriceBase(playbook, openingPrice, config.toString(), follow);
            priceStep.maxTime = DEFAULT_PRICE_STEP_TIME;
        }
        if ( priceStep.priceEdge==0 ) {
            if( tolerance==0 ) {
                tolerance = (long)(priceStep.priceBase*DEFAULT_PRICE_TOLERANCE_PERCENT);
            }
            if ( priceStep.range ) {
                priceStep.priceEdge = priceStep.priceBase+tolerance;
            } else {
                priceStep.priceEdge = priceStep.priceBase-tolerance;
            }
        }
        return priceStep;
    }

    /**
     * 构建PriceStep列表, 并排序
     */
    public static List<PriceStep> config2steps(Playbook playbook, long openingPrice, boolean follow, List configs){
        List<PriceStep> priceSteps = new ArrayList<>();
        for(int i=0;i<configs.size();i++) {
            Object config = configs.get(i);
            PriceStep priceStep = config2step(playbook, openingPrice, follow, config);
            priceSteps.add(priceStep);
        }
        Collections.sort(priceSteps, (PriceStep p1, PriceStep p2)->{
            return Long.compare(p1.priceBase, p2.priceBase);
        });
        return priceSteps;
    }

}