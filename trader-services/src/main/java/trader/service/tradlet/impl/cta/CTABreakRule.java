package trader.service.tradlet.impl.cta;

import org.jdom2.Element;
import org.ta4j.core.Bar;

import trader.common.tick.PriceLevel;
import trader.common.util.ConversionUtil;
import trader.common.util.PriceUtil;
import trader.service.md.MarketData;
import trader.service.ta.LeveledBarSeries;
import trader.service.ta.LongNum;
import trader.service.ta.TechnicalAnalysisAccess;
import trader.service.trade.TradeConstants.PosDirection;

/**
 * 简单突破策略
 */
public class CTABreakRule {
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
    public final long at;
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

    public CTABreakRule(CTAHint hint, int index, Element elem) {
        this.id = hint.id+":"+index;
        this.index = index;
        dir = hint.dir;
        disabled = ConversionUtil.toBoolean(elem.getAttribute("disabled"));
        at = PriceUtil.str2long(elem.getAttributeValue("at"));
        take = PriceUtil.str2long(elem.getAttributeValue("take"));
        stop = PriceUtil.str2long(elem.getAttributeValue("stop"));
        volume = ConversionUtil.toInt(elem.getAttribute("volume"));
        this.elem = elem;
    }

    /**
     * 是否当前行情匹配开仓规则
     */
    public boolean matcheOpen(MarketData md, TechnicalAnalysisAccess taAccess) {
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
                if ( md.lastPrice>=at ) {
                    long low = LongNum.fromNum(bar.getLowPrice()).rawValue();
                    long low0 = LongNum.fromNum(bar0.getLowPrice()).rawValue();

                    if ( low<at || low0<at ) {
                        result = true;
                    }
                }
            } else {
                //判断从上向下突破
                if ( md.lastPrice<=at ) {
                    long high = LongNum.fromNum(bar.getHighPrice()).rawValue();
                    long high0 = LongNum.fromNum(bar0.getHighPrice()).rawValue();

                    if ( high>at || high0>at ) {
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
                if ( tick.lastPrice<=this.stop ) {
                    result= true;
                }
            } else {
                if ( tick.lastPrice>=this.stop ) {
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
                if ( tick.lastPrice>=this.take ) {
                    result= true;
                }
            } else {
                if ( tick.lastPrice<=this.take ) {
                    result = true;
                }
            }
        }
        return result;
    }

    public String toString() {
        return elem.toString();
    }

}
