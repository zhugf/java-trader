package trader.service.tradlet.impl.cta;

import java.time.LocalDate;

import org.eclipse.jetty.util.StringUtil;
import org.jdom2.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.Bar;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import trader.common.tick.PriceLevel;
import trader.common.util.ConversionUtil;
import trader.common.util.DateUtil;
import trader.common.util.JsonEnabled;
import trader.common.util.PriceUtil;
import trader.service.md.MarketData;
import trader.service.ta.LeveledBarSeries;
import trader.service.ta.LongNum;
import trader.service.ta.TechnicalAnalysisAccess;
import trader.service.trade.TradeConstants.PosDirection;

/**
 * 简单突破策略
 */
public class CTARule implements JsonEnabled, Comparable<CTARule> {
    private final static Logger logger = LoggerFactory.getLogger(CTARule.class);

    public final CTAHint hint;

    /**
     * 唯一ID, 格式为 HintID:Idx
     */
    public final String id;
    /**
     * 索引
     */
    public final int index;
    /**
     * 方向
     */
    public final PosDirection dir;
    /**
     * 突破点
     */
    public final long enter;
    /**
     * 突破容忍点
     */
    public final long enterThreshold;
    /**
     * 止盈点
     */
    public final long take;
    /**
     * 止损点
     */
    public final long stop;
    /**
     * 手数
     */
    public final int volume;

    public final boolean disabled;

    private Element elem;

    public CTARule(CTAHint hint, int index, Element elem, LocalDate tradingDay) {
        this.hint = hint;
        this.index = index;
        dir = hint.dir;
        boolean disabled0 = hint.disabled || ConversionUtil.toBoolean(elem.getAttributeValue("disabled"));
        String strEnter = elem.getAttributeValue("enter");
        String strTake = elem.getAttributeValue("take");
        String strStop = elem.getAttributeValue("stop");
        String strVolume = elem.getAttributeValue("volume");
        //使用tradingDay过滤加载changelog
        for(Element changelog:elem.getChildren("changelog")) {
            LocalDate since = DateUtil.str2localdate(changelog.getAttributeValue("since"));
            if ( null==since || since.isAfter(tradingDay) ) {
                continue;
            }
            strEnter = attr2str(changelog, "enter", strEnter);
            strTake = attr2str(changelog, "take", strTake);
            strStop = attr2str(changelog, "stop", strStop);
            strVolume = attr2str(changelog, "volume", strVolume);
        }
        enter = PriceUtil.str2long(strEnter);
        take = PriceUtil.str2long(strTake);
        stop = PriceUtil.str2long(strStop);
        volume = ConversionUtil.toInt(strVolume);
        this.elem = elem;

        String id = elem.getAttributeValue("id");
        if ( StringUtil.isEmpty(id)) {
            id = hint.id+"-"+((int)(PriceUtil.long2price(enter)));
        }
        this.id = id;

        String errorReason = validate();
        if (!disabled0 && !StringUtil.isEmpty(errorReason)) {
            disabled0 = true;
            logger.warn("CTA 规则 "+id+" 检验失败, 自动禁用: "+errorReason);
        }
        disabled = disabled0;
        if ( !disabled ) {
            long space = Math.min( Math.abs(enter-stop)/4 , Math.abs(take-enter)/6);
            if ( dir==PosDirection.Long ) {
                enterThreshold = enter+space;
            } else {
                enterThreshold = enter-space;
            }
        } else {
            enterThreshold = 0;
        }
    }

    private static String attr2str(Element elem, String attrName, String defaultValue) {
        String result = elem.getAttributeValue(attrName);
        if ( StringUtil.isEmpty(result)) {
            result = defaultValue;
        }
        return result;
    }

    /**
     * 是否当前行情匹配不开仓撤退
     */
    public boolean matchDiscard(MarketData md) {
        boolean result = false;
        long priceTick = hint.instrument.getPriceTick();
        long lastPrice = md.lastPrice;
        if ( dir==PosDirection.Long) {
            if ( lastPrice<stop || lastPrice>(enter+priceTick*100) ) {
                result = true;
            }
        } else {
            if ( lastPrice>stop || lastPrice<(enter-priceTick*100)) {
                result = true;
            }
        }
        return result;
    }

    /**
     * 匹配开仓条件: 严格
     */
    public boolean matchEnterStrict(MarketData tick, TechnicalAnalysisAccess taAccess) {
        boolean result = false;
        long priceTick = hint.instrument.getPriceTick();
        LeveledBarSeries min1Series = taAccess.getSeries(PriceLevel.MIN1);
        Bar bar = min1Series.getLastBar();
        Bar bar0 = bar;
        if ( min1Series.getBarCount()>1 ) {
            bar0 = min1Series.getBar(min1Series.getBarCount()-2);
        }
        long lastPrice = tick.lastPrice; long askPrice = tick.lastAskPrice(); long bidPrice = tick.lastBidPrice();
        if ( dir==PosDirection.Long) {
            //判断从下向上突破
            long low = 0;
            long low0 = 0;
            long enterMax = (enter+priceTick*10);
            if ( (lastPrice>=enter|| askPrice>=enter||bidPrice>=enter) && lastPrice<=enterMax && bar!=null ) {
                low = LongNum.fromNum(bar.getLowPrice()).rawValue();
                low0 = LongNum.fromNum(bar0.getLowPrice()).rawValue();
                if ( low<=enter || low0<=enter) {
                    result = true;
                }
            }
            if ( logger.isDebugEnabled()) {
                logger.debug("Rule "+id+" enter "+PriceUtil.long2str(enter)+" enterMax "+PriceUtil.long2str(enterMax)+" matchEnter long dir result: "+result+" for "+tick+", lastPrice: "+PriceUtil.long2str(lastPrice)+" low: "+PriceUtil.long2str(low)+", low0: "+PriceUtil.long2str(low0) );
            }
        } else {
            long high = 0;
            long high0 = 0;
            long enterMin = (enter-priceTick*10);
            //判断从上向下突破
            if ( (lastPrice<=enter||askPrice<=enter||bidPrice<=enter) && lastPrice>=enterMin && bar!=null ) {
                high = LongNum.fromNum(bar.getHighPrice()).rawValue();
                high0 = LongNum.fromNum(bar0.getHighPrice()).rawValue();
                if ( high>=enter || high0>=enter) {
                    result = true;
                }
            }
            if ( logger.isDebugEnabled()) {
                logger.debug("Rule "+id+" enter "+PriceUtil.long2str(enter)+" enterMin "+PriceUtil.long2str(enterMin)+" matchEnter short dir result: "+result+" for "+tick+", lastPrice: "+PriceUtil.long2str(lastPrice)+" hig: "+PriceUtil.long2str(high)+", high0: "+PriceUtil.long2str(high0) );
            }
        }
        if ( result ) {
            logger.info("CTA "+id+" 匹配当前行情: "+tick);
        }
        return result;
    }

    /**
     * 宽松匹配开仓条件
     *
     * @param tick
     * @param taAccess
     * @return
     */
    public boolean matchEnterLoose(MarketData tick, TechnicalAnalysisAccess taAccess) {
        boolean result = false;
        long lastPrice = tick.lastPrice;
        if ( dir==PosDirection.Long) {
            if ( lastPrice>=enter && lastPrice<enterThreshold ) {
                result = true;
            }
        } else {
            if ( lastPrice<=enter && lastPrice>=enterThreshold ) {
                result = true;
            }
        }
        if ( result ) {
            logger.info("CTA "+id+" 宽松匹配当前行情: "+tick);
        }
        return result;
    }

    /**
     * 是否当前行情匹配止损规则
     */
    public boolean matchStop(MarketData tick) {
        boolean result = false;
        if ( dir==PosDirection.Long) {
            if ( this.stop>0 && tick.lastPrice<=this.stop ) {
                result= true;
            }
        } else {
            if ( this.stop>0 && tick.lastPrice>=this.stop ) {
                result = true;
            }
        }
        return result;
    }

    /**
     * 是否当前行情匹配止盈规则
     */
    public boolean matchTake(MarketData tick) {
        boolean result = false;
        if ( dir==PosDirection.Long) {
            if ( this.take>0 && tick.lastPrice>=this.take ) {
                result = true;
            }
        } else {
            if ( this.take>0 && tick.lastPrice<=this.take ) {
                result = true;
            }
        }
        return result;
    }

    /**
     * 是否最后5分钟
     */
    public boolean matchEnd(MarketData tick) {
        boolean result = false;
        if ( hint.dayEnd.equals(tick.mktTimes.getTradingDay()) ){
            if ( (tick.mktTimes.getTotalTradingMillis()-tick.mktTime) <= 5*60*1000 ) {
                result = true;
            }
        }
        return result;
    }

    public String toString() {
        return toJson().toString();
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("index", index);
        json.addProperty("dir", dir.name());
        json.addProperty("enter", PriceUtil.long2str(enter));
        json.addProperty("enterThreshold", PriceUtil.long2str(enterThreshold));
        json.addProperty("stop", PriceUtil.long2str(stop));
        json.addProperty("take", PriceUtil.long2str(take));
        json.addProperty("volume", volume);
        return json;
    }

    /**
     * 校验参数正确性, 如校验失败则自动disable
     * @return 失败理由
     */
    private String validate() {
        if ( enter<=0 || take<=0 || stop<=0 ) {
            return "enter<=0 || take<=0 || stop<=0";
        }
        if ( dir==PosDirection.Long ) {
            if ( take>enter && enter>stop) {
            } else {
                return "dir==Long && take>enter>stop";
            }
        }
        if (dir == PosDirection.Short) {
            if ( take<enter && enter<stop ) {
            } else {
                return "dir==Short && take<enter<stop";
            }
        }
        if ( volume<=0 ) {
            return "volume<=0";
        }
        return null;
    }

    @Override
    public int compareTo(CTARule o) {
        return id.compareTo(o.id);
    }

}
