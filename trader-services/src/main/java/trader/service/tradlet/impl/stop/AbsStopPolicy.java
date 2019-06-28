package trader.service.tradlet.impl.stop;

import trader.common.beans.BeansContainer;
import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableTradingTimes;
import trader.common.util.ConversionUtil;
import trader.common.util.DateUtil;
import trader.common.util.JsonEnabled;
import trader.common.util.PriceUtil;
import trader.service.md.MarketData;
import trader.service.trade.MarketTimeService;
import trader.service.trade.TradeConstants.PosDirection;
import trader.service.tradlet.Playbook;
import trader.service.tradlet.TradletConstants;

public abstract class AbsStopPolicy implements JsonEnabled, TradletConstants {

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
    public abstract String needStop(Playbook playbook, MarketData tick);

    /**
     * 检查两个时间戳之间的市场时间是否大于某个数值
     *
     * @param beginTime epoch millis
     * @param endTime epoch millis
     * @param marketTime
     * @return
     */
    protected boolean marketTimeGreateThan(Exchangeable e, long beginTime, long endTime, int marketTime) {
        if ( (endTime-beginTime) >= marketTime ) {
            if ( tradingTimes==null ) {
                tradingTimes = e.exchange().detectTradingTimes(e, mtService.getMarketTime());
            }
            int beginMarketTime = tradingTimes.getTradingTime(DateUtil.long2datetime(e.exchange().getZoneId(), beginTime));
            int endMarketTime = tradingTimes.getTradingTime(DateUtil.long2datetime(e.exchange().getZoneId(), endTime));

            if ( (endMarketTime-beginMarketTime)>=marketTime ) {
                return true;
            }
        }
        return false;
    }

    /**
     * 转换相对价格或tick数为long值
     */
    protected static long str2price(Exchangeable e, String priceStr) {
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
    protected static long getPriceBase(Playbook playbook, long openingPrice, String priceStr, boolean follow) {
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

}
