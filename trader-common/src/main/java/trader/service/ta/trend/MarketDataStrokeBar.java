package trader.service.ta.trend;

import java.time.ZonedDateTime;

import org.ta4j.core.num.Num;

import trader.common.util.PriceUtil;
import trader.service.md.MarketData;
import trader.service.ta.LongNum;
import trader.service.trade.TradeConstants.PosDirection;

/**
 * 缠轮笔划, 从细微的价格波动中找到的最基本的走势
 */
public class MarketDataStrokeBar extends WaveBar {

    private static final long serialVersionUID = -2463984410565197764L;

    protected Num threshold;
    private MarketData mdBegin;
    private MarketData mdMax;
    private MarketData mdMin;
    private MarketData mdEnd;

    @Override
    public WaveType getWaveType() {
        return WaveType.Stroke;
    }

    /**
     * 从单个行情切片创建笔划, 方向为Net 未知
     */
    public MarketDataStrokeBar(Num threshold, MarketData md) {
        this.threshold = threshold;
        mdBegin = mdMax = mdMin = mdEnd = md;
        begin = ZonedDateTime.of(md.updateTime, md.instrumentId.exchange().getZoneId());
        end = begin;
        open = max = min = close = new LongNum(md.lastPrice);
        volume = LongNum.ZERO;
        amount = LongNum.ZERO;
        direction = PosDirection.Net;
    }

    /**
     * 从开始结束两个行情切片创建笔划, 方向为Long/Short
     * @param threshold
     * @param md
     * @param md2
     */
    public MarketDataStrokeBar(Num threshold, MarketData md, MarketData md2) {
        this.threshold = threshold;
        mdBegin = md;
        mdEnd = md2;
        begin = ZonedDateTime.of(md.updateTime, md.instrumentId.exchange().getZoneId());
        end = ZonedDateTime.of(md2.updateTime, md2.instrumentId.exchange().getZoneId());
        open = new LongNum(md.lastPrice);
        close = new LongNum(md2.lastPrice);
        volume = new LongNum(md2.volume - md.volume);
        amount = new LongNum(md2.turnover - md.turnover);
        if ( md.lastPrice<md2.lastPrice ) {
            direction = PosDirection.Long;
            mdMax = md2;
            mdMin = md;
            max = close;
            min = open;
        }else {
            direction = PosDirection.Short;
            mdMax = md;
            mdMin = md2;
            max = open;
            min = close;
        }
    }

    /**
     * 更新行情切片
     *
     * @return true 如果需要拆分当前笔划, false 不需要, 当前笔划继续.
     */
    public void update(MarketData md) {
        mdEnd = md;
        end = ZonedDateTime.of(md.updateTime, md.instrumentId.exchange().getZoneId());
        close = new LongNum(md.lastPrice);
        volume = new LongNum(PriceUtil.price2long(md.volume - mdBegin.volume));
        amount = new LongNum(md.turnover - mdBegin.turnover);
        if (mdMax.lastPrice < md.lastPrice) {
            mdMax = md;
            max = close;
        }
        if (mdMin.lastPrice > md.lastPrice) {
            mdMin = md;
            min = close;
        }
        //检测方向
        if (direction == PosDirection.Net) {
            if (open.isLessThan(close.minus(threshold))) {
                direction = PosDirection.Long;
            } else if (open.isGreaterThan(close.plus(threshold))) {
                direction = PosDirection.Short;
            }
        }
    }

    /**
     * 判断是否需要拆分出新笔划
     */
    @Override
    public boolean needSplit() {
        boolean result = false;
        switch(direction) {
        case Long:
            //向上笔划, 最高点向下超出阈值, 需要拆分
            result = max.isGreaterThan(close.plus(threshold));
            break;
        case Short:
            //向下笔划, 最低点向上超出阈值, 需要拆分
            result = min.isLessThan(close.min(threshold));
            break;
        case Net:
            break;
        }
        return result;
    }

    /**
     * 拆分出一个与当前笔划方向相反的新笔划
     */
    @Override
    public WaveBar split() {
        MarketDataStrokeBar result = null;

        MarketData md0=null, md1=null;
        switch(direction) {
        case Long:
            //向上笔划, 从最高点拆分
            md0=mdMax; md1=mdEnd;
            this.mdEnd = mdMax;
            this.close = max;
            if ( mdMin.updateTimestamp>mdEnd.updateTimestamp ) {
                mdMin = min(mdBegin, mdEnd);
            }
            break;
        case Short:
            md0=mdMin; md1=mdEnd;
            this.mdEnd = mdMin;
            this.close = min;
            if ( mdMax.updateTimestamp>mdEnd.updateTimestamp ) {
                mdMax = min(mdBegin, mdEnd);
            }
            break;
        case Net:
            break;
        }
        if ( md0!=null ) {
            result = new MarketDataStrokeBar(threshold, md0, md1);
        }
        return result;
    }

    private static MarketData max(MarketData md, MarketData md2) {
        MarketData result = null;
        if ( md.lastPrice>md2.lastPrice) {
            result= md;
        }else {
            result= md2;
        }
        return result;
    }

    private static MarketData min(MarketData md, MarketData md2) {
        MarketData result = null;
        if ( md.lastPrice<md2.lastPrice) {
            result= md;
        }else {
            result= md2;
        }
        return result;
    }
}
