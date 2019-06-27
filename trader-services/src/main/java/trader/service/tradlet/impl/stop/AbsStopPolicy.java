package trader.service.tradlet.impl.stop;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import trader.common.beans.BeansContainer;
import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableTradingTimes;
import trader.common.util.ConversionUtil;
import trader.common.util.DateUtil;
import trader.common.util.JsonEnabled;
import trader.common.util.PriceUtil;
import trader.service.trade.MarketTimeService;
import trader.service.trade.TradeConstants.PosDirection;
import trader.service.tradlet.Playbook;
import trader.service.tradlet.TradletConstants;

public abstract class AbsStopPolicy implements JsonEnabled, TradletConstants {

    public static class PriceStep implements JsonEnabled{
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

        /**
         * 是否已经达到这一价格阶梯
         */
        boolean meet;

        @Override
        public JsonElement toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("priceRange", priceRange);
            json.addProperty("priceBase", priceBase);
            json.addProperty("seconds", seconds);
            json.addProperty("beginMillis", beginMillis);
            json.addProperty("lastMillis", lastMillis);
            return json;
        }
    }

    protected MarketTimeService mtService;
    protected ExchangeableTradingTimes tradingTimes;

    AbsStopPolicy(BeansContainer beansContainer){
        mtService = beansContainer.getBean(MarketTimeService.class);
    }

    /**
     * 是否需要止损
     *
     * @return null代表不需要
     */
    public abstract String needStop(Playbook playbook, long newPrice);


    /**
     * 检查两个时间戳之间的市场时间是否大于某个数值
     *
     * @param beginTime epoch millis
     * @param endTime epoch millis
     * @param marketSeconds
     * @return
     */
    protected boolean marketTimeGreateThan(Exchangeable e, long beginTime, long endTime, int marketSeconds) {
        if ( (endTime-beginTime)/1000 >= marketSeconds ) {
            if ( tradingTimes==null ) {
                tradingTimes = e.exchange().detectTradingTimes(e, mtService.getMarketTime());
            }
            int beginMarketTime = tradingTimes.getTradingTime(DateUtil.long2datetime(e.exchange().getZoneId(), beginTime));
            int endMarketTime = tradingTimes.getTradingTime(DateUtil.long2datetime(e.exchange().getZoneId(), endTime));

            if ( (endMarketTime-beginMarketTime)>=marketSeconds ) {
                return true;
            }
        }
        return false;
    }

    /**
     * 转换相对价格或tick数为long值
     */
    protected long str2price(Exchangeable e, String priceStr) {
        long result = 0;
        priceStr = priceStr.trim().toLowerCase();
        if (priceStr.endsWith("t")) { //5t, 10t
            long priceTick = e.getPriceTick();
            int unit = ConversionUtil.toInt(priceStr.substring(0, priceStr.length() - 1));
            result = unit*priceTick;
        } else if ( priceStr.endsWith("l")){ //10000l = 1.0000
            result = ConversionUtil.toLong(priceStr.substring(0, priceStr.length()-1));
        }else {
            result = PriceUtil.price2long(ConversionUtil.toDouble(priceStr, true));
        }
        return result;
    }

    /**
     * 获得止损价格
     *
     * @param priceStr 相对的止损价格 5t,10t 或绝对价格 5520
     * @param follow true 顺势用于止盈, false 逆势用于止损
     */
    protected long getPriceBase(Playbook playbook, long openingPrice, String priceStr, boolean follow) {
        long result = 0;
        long stopPrice = str2price(playbook.getExchangable(), priceStr);
        boolean related = openingPrice/10>stopPrice;
        if ( related ) {
            long unit=1;
            if ( !follow ) {
                unit = -1;
            }
            if ( playbook.getDirection()==PosDirection.Short) {
                unit *= -1;
            }
            //follow, direction,  result
            // true     true        +
            // false    true        -
            // true     false       -
            // false    false       +
            result = openingPrice + unit*stopPrice;
        } else {
            result = stopPrice;
        }
        return result;
    }

    /**
     * 构建PriceStep列表, 并排序
     */
    protected List<PriceStep> config2steps(Playbook playbook, long openingPrice, boolean follow, JsonElement priceStepsElem){
        List<PriceStep> priceSteps = new ArrayList<>();
        if ( priceStepsElem instanceof JsonObject ) {
            JsonObject priceStepsJson = (JsonObject)priceStepsElem;
            for(String key:priceStepsJson.keySet()) {
                String val = priceStepsJson.get(key).getAsString();
                PriceStep priceStep = new PriceStep();
                priceStep.priceBase = getPriceBase(playbook, openingPrice, key, follow);
                priceStep.seconds = (int)ConversionUtil.str2seconds(val);
                priceSteps.add(priceStep);
            }
        } else {
            JsonArray priceStepsArray = (JsonArray)priceStepsElem;
            for(int i=0;i<priceStepsArray.size();i++) {
                JsonElement priceStepElem = priceStepsArray.get(i);
                if ( priceStepElem instanceof JsonObject ) {
                    JsonObject priceStepJson = (JsonObject)priceStepElem;
                    PriceStep priceStep = new PriceStep();
                    priceStep.priceBase = getPriceBase(playbook, openingPrice, priceStepJson.get("priceBase").getAsString(), follow);
                    priceStep.seconds = (int)ConversionUtil.str2seconds(priceStepJson.get("duration").getAsString());
                    priceSteps.add(priceStep);
                }else {
                    PriceStep priceStep = new PriceStep();
                    priceStep.priceBase = getPriceBase(playbook, openingPrice, priceStepElem.getAsString(), follow);
                    priceStep.seconds =  (int)ConversionUtil.str2seconds(DEFAULT_PRICE_STEP_SECONDS);
                    priceSteps.add(priceStep);
                }
            }
        }
        for(PriceStep priceStep:priceSteps) {
            if ( playbook.getDirection()==PosDirection.Long ) {
                priceStep.priceRange = follow;
            }else {
                priceStep.priceRange = !follow;
            }
        }
        //排序
        boolean reverse = playbook.getDirection()==PosDirection.Short;
        if ( !follow ) {
            reverse = !reverse;
        }
        Collections.sort(priceSteps, (PriceStep p1, PriceStep p2)->{
            return Long.compare(p1.priceBase, p2.priceBase);
        });
        if ( reverse ) {
            Collections.reverse(priceSteps);
        }
        return priceSteps;
    }

}
