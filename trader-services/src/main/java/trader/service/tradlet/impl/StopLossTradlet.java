package trader.service.tradlet.impl;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import trader.common.beans.BeansContainer;
import trader.common.beans.Discoverable;
import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableTradingTimes;
import trader.common.util.ConversionUtil;
import trader.common.util.DateUtil;
import trader.common.util.JsonEnabled;
import trader.common.util.PriceUtil;
import trader.common.util.StringUtil;
import trader.service.md.MarketData;
import trader.service.md.MarketDataService;
import trader.service.ta.LeveledTimeSeries;
import trader.service.trade.MarketTimeService;
import trader.service.trade.TradeConstants.PosDirection;
import trader.service.tradlet.Playbook;
import trader.service.tradlet.PlaybookCloseReq;
import trader.service.tradlet.PlaybookKeeper;
import trader.service.tradlet.PlaybookStateTuple;
import trader.service.tradlet.Tradlet;
import trader.service.tradlet.TradletConstants;
import trader.service.tradlet.TradletContext;
import trader.service.tradlet.TradletGroup;

/**
 * 简单止损策略, 用于开仓后一段时间内止损, 需要Playbook属性中明确止损幅度.
 * <BR>目前使用止损方式
 * <LI>价格阶梯止损: 在某个价格之上保持一段时间即止损.
 * <LI>最长持仓时间: 到达最大持仓时间后, 即平仓
 * <LI>最后持仓时间: 到达某绝对市场时间, 即平仓
 *
 * 需要为每个playbook实例构建运行时数据, 保证tradlet重新加载后可用.
 */
@Discoverable(interfaceClass = Tradlet.class, purpose = "StopLoss")
public class StopLossTradlet implements Tradlet, TradletConstants {
    private final static Logger logger = LoggerFactory.getLogger(StopLossTradlet.class);

    public static class StopLossPriceStep implements JsonEnabled{
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

        @Override
        public JsonElement toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("priceRange", priceRange);
            json.addProperty("priceBase", priceBase);
            json.addProperty("seconds", priceBase);
            json.addProperty("beginMillis", beginMillis);
            json.addProperty("lastMillis", lastMillis);
            return json;
        }

    }

    private BeansContainer beansContainer;
    private MarketDataService mdService;
    private MarketTimeService mtService;
    private TradletGroup group;
    private PlaybookKeeper playbookKeeper;

    @Override
    public void init(TradletContext context) {
        beansContainer = context.getBeansContainer();
        group = context.getGroup();
        playbookKeeper = context.getGroup().getPlaybookKeeper();
        mdService = beansContainer.getBean(MarketDataService.class);
        mtService = beansContainer.getBean(MarketTimeService.class);
    }

    @Override
    public void destroy() {

    }

    @Override
    public void onPlaybookStateChanged(Playbook playbook, PlaybookStateTuple oldStateTuple) {
        if ( oldStateTuple==null ) {
            //从Playbook 属性构建运行时数据.
            playbook.setAttr(PBATTR_STOPLOSS_RUNTIME, buildRuntime(playbook));
        }
    }

    @Override
    public void onTick(MarketData marketData) {
        checkActivePlaybooks(marketData);
    }

    @Override
    public void onNewBar(LeveledTimeSeries series) {
    }

    @Override
    public void onNoopSecond() {
        checkActivePlaybooks(null);
    }

    private void checkActivePlaybooks(MarketData md) {
        for(Playbook playbook:playbookKeeper.getActivePlaybooks(null)) {
            long newPrice = 0;
            if ( md!=null && playbook.getExchangable().equals(md.instrumentId) ) {
                newPrice = md.lastPrice;
            }
            String closeReason = needStopLoss(playbook, newPrice);
            if ( closeReason!=null ) {
                PlaybookCloseReq closeReq = new PlaybookCloseReq();
                closeReq.setActionId(closeReason);
                playbookKeeper.closePlaybook(playbook, closeReq);
            }
        }
    }

    /**
     * 检查是否需要立刻止损
     */
    private String needStopLoss(Playbook playbook, long newPrice) {
        Object[] runtime = (Object[])playbook.getAttr(PBATTR_STOPLOSS_RUNTIME);
        if ( runtime==null ) {
            return null;
        }
        //PriceStep
        StopLossPriceStep[] priceSteps = (StopLossPriceStep[])runtime[StopLossPolicy.PriceStep.ordinal()];
        if ( newPrice>0 && priceSteps!=null ) {
            long stopPrice = needStopLossPriceStop(playbook, priceSteps, newPrice);
            if ( stopPrice!=0) {
                return StopLossPolicy.PriceStep.name()+" "+PriceUtil.long2str(stopPrice);
            }
        }

        //MaxLifeTime
        Integer maxLifeSeconds = (Integer)runtime[StopLossPolicy.MaxLifeTime.ordinal()];
        if ( maxLifeSeconds!=null ) {
            // 简单时间戳简单比较后, 再用市场时间进一步比较确认.
            long beginTime = playbook.getStateTuples().get(0).getTimestamp();
            long currTime = mtService.currentTimeMillis();
            if ( marketTimeGreateThan(playbook.getExchangable(), beginTime, currTime, maxLifeSeconds) ){
                return StopLossPolicy.MaxLifeTime.name();
            }
        }

        //EndTime
        Long endTimeEpochMillis = (Long)runtime[StopLossPolicy.EndTime.ordinal()];
        if ( endTimeEpochMillis!=null ) {
            long currentTimeMillis = mtService.currentTimeMillis();
            if ( currentTimeMillis>=endTimeEpochMillis ) {
                return StopLossPolicy.EndTime.name();
            }
        }
        return null;
    }

    /**
     * 检查两个时间戳之间的市场时间是否大于某个数值
     *
     * @param beginMillis epoch millis
     * @param endMillis epoch millis
     * @param marketSeconds
     * @return
     */
    private boolean marketTimeGreateThan(Exchangeable e, long beginMillis, long endMillis, int marketSeconds) {
        if ( (endMillis-beginMillis)/1000 >= marketSeconds ) {
            ExchangeableTradingTimes tradingDay = e.exchange().detectTradingTimes(e, mtService.getMarketTime());
            if ( tradingDay!=null) {
                int beginMarketTime = tradingDay.getTradingTime(DateUtil.long2datetime(e.exchange().getZoneId(), beginMillis));
                int endMarketTime = tradingDay.getTradingTime(DateUtil.long2datetime(e.exchange().getZoneId(), endMillis));

                if ( (endMarketTime-beginMarketTime)>=marketSeconds ) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 检查是否需要阶梯价格止损
     */
    private long needStopLossPriceStop(Playbook playbook, StopLossPriceStep[] priceSteps, long newPrice) {
        long currTimeMillis = mtService.currentTimeMillis();
        int clearIndex = -1;
        for(int i=0;i<priceSteps.length;i++) {
            StopLossPriceStep priceStep = priceSteps[i];
            if ( priceStep.priceRange ) { //价格>=priceBase
                if ( newPrice>=priceStep.priceBase) {
                    extendPriceStep(priceStep, currTimeMillis);
                } else {
                    clearIndex = i;
                    break;
                }
            }else { //价格<=priceBase
                if ( newPrice<=priceStep.priceBase ) {
                    extendPriceStep(priceStep, currTimeMillis);
                } else {
                    clearIndex = i;
                    break;
                }
            }
            //检查价格阶梯时间是否达到
            if ( marketTimeGreateThan(playbook.getExchangable(), priceStep.beginMillis, priceStep.lastMillis, priceStep.seconds) ){
                return priceStep.priceBase;
            }
        }
        //后面的价格统统清除
        if ( clearIndex>0 ) {
            for(int i=clearIndex; i<priceSteps.length;i++) {
                priceSteps[i].beginMillis = 0;
                priceSteps[i].lastMillis = 0;
            }
        }
        return 0;
    }

    /**
     * 延续PriceStep时间
     */
    private void extendPriceStep(StopLossPriceStep priceStep, long currTimeMillis) {
        priceStep.lastMillis = currTimeMillis;
        if ( priceStep.beginMillis==0) {
            priceStep.beginMillis = currTimeMillis;
        }
    }

    private Object[] buildRuntime(Playbook playbook) {
        Object[] result = new Object[StopLossPolicy.values().length];

        //PriceStep
        String priceStepsStr = ConversionUtil.toString(playbook.getAttr(PBATTR_STOPLOSS_PRICE_STEPS));
        if ( !StringUtil.isEmpty(priceStepsStr) ) {
            long openingPrice = playbook.getMoney(PBMny_Opening);
            if ( openingPrice==0 ) {
                openingPrice = mdService.getLastData(playbook.getExchangable()).lastPrice;
            }
            List<StopLossPriceStep> priceSteps = new ArrayList<>();
            for(String[] kv:StringUtil.splitKVs(priceStepsStr)) {
                StopLossPriceStep priceStep = new StopLossPriceStep();
                priceSteps.add(priceStep);

                priceStep.priceBase = str2price(playbook, openingPrice, kv[0]);
                if ( kv.length>1 ) {
                    priceStep.seconds = (int)ConversionUtil.str2seconds(kv[1]);
                }
                if ( playbook.getDirection()==PosDirection.Long ) {
                    priceStep.priceRange = false;
                }else {
                    priceStep.priceRange = true;
                }
            }
            //从小到大排序
            Collections.sort(priceSteps, (StopLossPriceStep p1, StopLossPriceStep p2)->{
                return Long.compare(p1.priceBase, p2.priceBase);
            });
            //如果是开多仓, 需要逆序
            if ( playbook.getDirection()==PosDirection.Long ) {
                Collections.reverse(priceSteps);
            }
            result[StopLossPolicy.PriceStep.ordinal()] = priceSteps.toArray(new StopLossPriceStep[priceSteps.size()]);
        }

        //MaxLifeTime
        String maxLifeTimeStr = ConversionUtil.toString(playbook.getAttr(PBATTR_STOPLOSS_MAX_LIFE_TIME));
        if ( !StringUtil.isEmpty(maxLifeTimeStr) ) {
            int maxLifeSeconds = (int)ConversionUtil.str2seconds(maxLifeTimeStr);
            if ( maxLifeSeconds<=0 ) {
                logger.error("Tradlet group "+group.getId()+" playbook "+playbook.getId()+" parse stop loss policy maxLifeTime failed: "+maxLifeTimeStr);
            } else {
                result[StopLossPolicy.MaxLifeTime.ordinal()] = maxLifeSeconds;
            }
        }

        //EndTime
        String endTimeStr = ConversionUtil.toString(playbook.getAttr(PBATTR_STOPLOSS_END_TIME));
        if ( !StringUtil.isEmpty(endTimeStr) ) {
            LocalTime endTime = DateUtil.str2localtime(endTimeStr);
            if ( endTime==null ) {
                logger.error("Tradlet group "+group.getId()+" playbook "+playbook.getId()+" parse stop loss policy endTime failed: "+endTimeStr);
            } else {
                LocalDateTime currTime = mtService.getMarketTime();
                LocalDateTime endTime0 = endTime.atDate(currTime.toLocalDate());
                if ( endTime0.isBefore(currTime)) { //如果 00:55:05 < 21:00:00, 那么加1天, 应对夜市隔日场景
                    endTime0 = endTime0.plusDays(1);
                }
                Instant endInstant = endTime0.atZone(playbook.getExchangable().exchange().getZoneId()).toInstant();
                result[StopLossPolicy.EndTime.ordinal()] = endInstant.toEpochMilli();
            }
        }
        return result;
    }

    /**
     * 转换绝对或相对价格为long数值
     */
    private long str2price(Playbook playbook, long openingPrice, String priceStr) {
        long result = 0;
        priceStr = priceStr.trim().toLowerCase();
        if (priceStr.endsWith("t")) { //5t, 10t
            long priceTick = playbook.getExchangable().getPriceTick();
            int unit = ConversionUtil.toInt(priceStr.substring(0, priceStr.length() - 1));
            if (playbook.getDirection() == PosDirection.Long) {
                result = openingPrice-unit*priceTick;
            } else {
                result = openingPrice+unit*priceTick;
            }
        } else { // 275.75
            result = PriceUtil.price2long(ConversionUtil.toDouble(priceStr, true));
        }
        return result;
    }

}
