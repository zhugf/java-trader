package trader.service.tradlet.impl.cta;

import java.time.LocalDate;

import org.eclipse.jetty.util.StringUtil;
import org.jdom2.Element;
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
public class CTARule implements JsonEnabled {

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
        String id = elem.getAttributeValue("id");
        if ( StringUtil.isEmpty(id)) {
            id = hint.id+":"+index;
        }
        this.id = id;
        this.index = index;
        dir = hint.dir;
        disabled = ConversionUtil.toBoolean(elem.getAttributeValue("disabled"));
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
    public boolean matchDiscard(MarketData tick) {
        return false;
    }

    /**
     * 是否当前行情匹配开仓规则
     */
    public boolean matchEnter(MarketData md, TechnicalAnalysisAccess taAccess) {
        boolean result = false;
        if ( !disabled ) {
            LeveledBarSeries min1Series = taAccess.getSeries(PriceLevel.MIN1);
            Bar bar = min1Series.getLastBar();
            Bar bar0 = bar;
            if ( min1Series.getBarCount()>1 ) {
                bar0 = min1Series.getBar(min1Series.getBarCount()-2);
            }
            if ( dir==PosDirection.Long) {
                //判断从下向上突破
                if ( md.lastPrice>=enter && bar!=null ) {
                    long low = LongNum.fromNum(bar.getLowPrice()).rawValue();
                    long low0 = LongNum.fromNum(bar0.getLowPrice()).rawValue();

                    if ( low<enter || low0<enter ) {
                        result = true;
                    }
                }
            } else {
                //判断从上向下突破
                if ( md.lastPrice<=enter && bar!=null ) {
                    long high = LongNum.fromNum(bar.getHighPrice()).rawValue();
                    long high0 = LongNum.fromNum(bar0.getHighPrice()).rawValue();

                    if ( high>enter || high0>enter ) {
                        result = true;
                    }
                }
            }
        }
        return result;
    }

    /**
     * 是否当前行情匹配止损规则
     */
    public boolean matchStop(MarketData tick) {
        boolean result = false;
        if ( !disabled ) {
            if ( dir==PosDirection.Long) {
                if ( this.stop>0 && tick.lastPrice<=this.stop ) {
                    result= true;
                }
            } else {
                if ( this.stop>0 && tick.lastPrice>=this.stop ) {
                    result = true;
                }
            }
        }
        return result;
    }

    /**
     * 是否当前行情匹配止盈规则
     */
    public boolean matchTake(MarketData tick) {
        boolean result = false;
        if ( !disabled ) {
            if ( dir==PosDirection.Long) {
                if ( this.take>0 && tick.lastPrice>=this.take ) {
                    result= true;
                }
            } else {
                if ( this.take>0 && tick.lastPrice<=this.take ) {
                    result = true;
                }
            }
        }
        return result;
    }

    /**
     * 是否最后5分钟
     */
    public boolean matchEnd(MarketData tick) {
        boolean result = false;
        if ( hint.dayEnd.equals(tick.updateTime.toLocalDate()) ){
            if ( (tick.mktTimes.getTotalTradingMillis()-tick.mktTime)>= 5*60*1000 ) {
                result = true;
            }
        }
        return result;
    }

    public String toString() {
        return elem.toString();
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("index", index);
        json.addProperty("dir", dir.name());
        json.addProperty("at", PriceUtil.long2str(enter));
        json.addProperty("stop", PriceUtil.long2str(stop));
        json.addProperty("take", PriceUtil.long2str(take));
        json.addProperty("volume", volume);
        return json;
    }

}
