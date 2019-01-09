package trader.service.ta.trend;

import java.time.Duration;
import java.time.ZonedDateTime;

import org.ta4j.core.num.Num;

import trader.common.exchangeable.Exchangeable;
import trader.common.util.DateUtil;
import trader.common.util.PriceUtil;
import trader.service.md.MarketData;
import trader.service.ta.LongNum;
import trader.service.trade.TradeConstants.PosDirection;

/**
 * 缠轮笔划, 从细微的价格波动中找到的最基本的走势
 */
public class MarketDataStrokeBar extends WaveBar<MarketData> {

    private static final long serialVersionUID = -2463984410565197764L;

    protected Num threshold;
    private MarketData mdOpen;
    private MarketData mdMax;
    private MarketData mdMin;
    private MarketData mdClose;
    private MarketData mdSplit;

    @Override
    public WaveType getWaveType() {
        return WaveType.Stroke;
    }

    /**
     * 从单个行情切片创建笔划, 方向为Net 未知
     */
    public MarketDataStrokeBar(Num threshold, MarketData md) {
        this.threshold = threshold;
        mdOpen = mdMax = mdMin = mdClose = md;
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
        mdOpen = md;
        mdClose = md2;
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
    @Override
    public WaveBar<MarketData> update(WaveBar<MarketData> prev, MarketData md) {
        mdClose = md;
        end = ZonedDateTime.of(md.updateTime, md.instrumentId.exchange().getZoneId());
        close = new LongNum(md.lastPrice);
        if (mdMax.lastPrice < md.lastPrice) {
            mdMax = md;
            max = close;
        }
        if (mdMin.lastPrice > md.lastPrice) {
            mdMin = md;
            min = close;
        }
        updateVol();
        //检测方向
        if (direction == PosDirection.Net) {
            if (open.isLessThan(close.minus(threshold))) {
                direction = PosDirection.Long;
            } else if (open.isGreaterThan(close.plus(threshold))) {
                direction = PosDirection.Short;
            }
        }
        if( needSplit() ) {
            return split();
        }
        return null;
    }

    @Override
    public Exchangeable getExchangeable() {
        return mdOpen.instrumentId;
    }

    @Override
    public boolean canMerge() {
        return false;
    }

    @Override
    public void merge(WaveBar bar) {
        throw new UnsupportedOperationException("merge operation is not supported");
    }

    /**
     * 判断是否需要拆分出新笔划
     */
    private boolean needSplit() {
        boolean result = false;
        switch(direction) {
        case Long:
            //向上笔划, 最高点向下超出阈值, 需要拆分
            result = max.isGreaterThan(close.plus(threshold));
            break;
        case Short:
            //向下笔划, 最低点向上超出阈值, 需要拆分
            result = min.isLessThan(close.minus(threshold));
            break;
        case Net:
            break;
        }
        return result;
    }

    /**
     * 拆分出一个与当前笔划方向相反的新笔划
     */
    private WaveBar<MarketData> split() {
        MarketDataStrokeBar result = null;

        MarketData md0=null, md1=null;
        switch(direction) {
        case Long:
            //向上笔划, 从最高点拆分, 新笔划向下
            md0=mdMax; md1=mdClose;
            this.mdClose = mdMax;
            this.close = max;
            this.end = ZonedDateTime.of(mdMax.updateTime, mdMax.instrumentId.exchange().getZoneId());
            if ( mdMin.updateTimestamp>mdClose.updateTimestamp ) {
                mdMin = min(mdOpen, mdClose);
            }
            updateVol();
            break;
        case Short:
            //向下笔划, 从最低的拆分, 新笔划向上
            md0=mdMin; md1=mdClose;
            this.mdClose = mdMin;
            this.close = min;
            this.end = ZonedDateTime.of(mdMin.updateTime, mdMin.instrumentId.exchange().getZoneId());
            if ( mdMax.updateTimestamp>mdClose.updateTimestamp ) {
                mdMax = max(mdOpen, mdClose);
            }
            updateVol();
            break;
        case Net:
            break;
        }
        if ( md0!=null ) {
            result = new MarketDataStrokeBar(threshold, md0, md1);
            mdSplit = md1;
        }
        return result;
    }

    private void updateVol() {
        volume = new LongNum(PriceUtil.price2long(mdClose.volume - mdOpen.volume));
        amount = new LongNum(mdClose.turnover - mdOpen.turnover);
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

    @Override
    public String toString() {
        Duration dur= this.getTimePeriod();
        return "Stroke[ "+direction+", B "+DateUtil.date2str(begin.toLocalDateTime())+", "+dur.toSeconds()+"S, O "+open+" C "+close+" H "+max+" L "+min+" ]";
    }

}
