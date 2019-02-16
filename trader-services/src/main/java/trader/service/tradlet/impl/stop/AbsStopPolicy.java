package trader.service.tradlet.impl.stop;

import trader.common.beans.BeansContainer;
import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableTradingTimes;
import trader.common.util.ConversionUtil;
import trader.common.util.DateUtil;
import trader.common.util.JsonEnabled;
import trader.common.util.PriceUtil;
import trader.service.trade.MarketTimeService;
import trader.service.tradlet.Playbook;

public abstract class AbsStopPolicy implements JsonEnabled {

    protected MarketTimeService mtService;
    protected ExchangeableTradingTimes tradingTimes;

    AbsStopPolicy(BeansContainer beansContainer){

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
        } else {
            result = PriceUtil.price2long(ConversionUtil.toDouble(priceStr, true));
        }
        return result;
    }


}
