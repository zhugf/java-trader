package trader.service.ta.trend;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.ta4j.core.num.Num;

import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableTradingTimes;
import trader.common.util.DateUtil;
import trader.common.util.PriceUtil;
import trader.service.md.MarketData;
import trader.service.ta.Bar2;
import trader.service.ta.LongNum;
import trader.service.trade.TradeConstants.PosDirection;

/**
 * 基于Bar2创建笔划
 */
public class SimpleStrokeBar extends WaveBar<Bar2>  {

    private static final long serialVersionUID = -938400652411706524L;

    protected WaveBarOption option;
    private Bar2 barOpen;
    private Bar2 barClose;
    private Bar2 barMax;
    private int barMaxIdx;
    private Bar2 barMin;
    private int barMinIdx;
    private Duration duration;
    private ArrayList<Bar2> bars = new ArrayList<>();
    private ExchangeableTradingTimes tradingTimes = null;

    public SimpleStrokeBar(WaveBarOption option, Bar2 bar) {
        this.option = option;

        MarketData mdClose = bar.getCloseTick();
        Exchangeable e = mdClose.instrument;
        tradingTimes = e.exchange().detectTradingTimes(e, mdClose.updateTime);

        begin = bar.getBeginTime();
        barOpen = bar;
        open = option.strokeBarPriceGetter.getPrice(bar);

        close = open;
        barClose = bar;

        max = open;
        barMax = barOpen;

        min = open;
        barMin = barOpen;
        volume = LongNum.ZERO;
        amount = LongNum.ZERO;
        direction = PosDirection.Net;
        update(null, bar);
    }

    public SimpleStrokeBar(WaveBarOption option, List<Bar2> bars, PosDirection dir) {
        this(option, bars.get(0));
        this.direction = dir;
        for(int i=1; i<bars.size();i++) {
            update(null, bars.get(i));
        }
    }

    @Override
    public MarketData getOpenTick() {
        return barOpen.getOpenTick();
    }

    @Override
    public MarketData getCloseTick() {
        return barClose.getCloseTick();
    }

    @Override
    public MarketData getMaxTick() {
        return barMax.getMaxTick();
    }

    @Override
    public MarketData getMinTick() {
        return barMin.getMinTick();
    }

    @Override
    public Duration getTimePeriod(){
        if ( duration==null ){
            int beginMillis = tradingTimes.getTradingTime(begin.toLocalDateTime());
            int endMillis = tradingTimes.getTradingTime(end.toLocalDateTime());
            duration = Duration.of(endMillis-beginMillis, ChronoUnit.MILLIS);
        }
        return duration;
    }

    @Override
    public WaveBar update(WaveBar<Bar2> prev, Bar2 bar) {
        close = option.strokeBarPriceGetter.getPrice(bar);
        barClose = bar;

        end = bar.getEndTime();

        if ( max.isLessThanOrEqual(close) ) {
            max = close; barMax = barClose; barMaxIdx = bars.size();
        }
        if ( min.isGreaterThanOrEqual(close)) {
            min = close; barMin = barClose; barMinIdx = bars.size();
        }
        updateVol();
        bars.add(bar);
        //更新状态
        if (bars.size()>=2 && direction == PosDirection.Net) {
            if (open.isLessThanOrEqual(close.minus(option.strokeThreshold))) {
                direction = PosDirection.Long;
            } else if (open.isGreaterThanOrEqual(close.plus(option.strokeThreshold))) {
                direction = PosDirection.Short;
            }
        }
        if ( needSplit()) {
            return split();
        }
        return null;
    }

    @Override
    public boolean canMerge() {
        return false;
    }

    @Override
    public void merge(WaveBar bar) {
        throw new UnsupportedOperationException("merge operation is not supported");
    }

    @Override
    public String toString() {
        Duration dur= this.getTimePeriod();
        return "Stroke[ "+direction+", B "+DateUtil.date2str(begin.toLocalDateTime())+", "+dur.getSeconds()+"S, O "+open+" C "+close+" H "+open.minus(close).abs()+", "+bars.size()+" bars ]";
    }

    private void updateVol() {
        MarketData mdOpen = getOpenTick();
        MarketData mdClose = barClose.getCloseTick();

        long vol = mdClose.volume-mdOpen.volume;
        this.volume = LongNum.fromRawValue( PriceUtil.price2long(vol) );
        openInterest = barClose.getOpenInterest();
        mktAvgPrice = barClose.getMktAvgPrice();
        //重新计算avgprice
        if ( vol==0 ) {
            avgPrice = mktAvgPrice;
            amount = LongNum.fromRawValue(mdClose.turnover);
        } else {
            avgPrice = LongNum.fromRawValue( (mdClose.turnover - mdOpen.turnover)/vol );
            amount = LongNum.fromRawValue(mdClose.turnover-mdOpen.turnover);
        }
        duration = null;
    }

    /**
     * 判断是否需要拆分出新笔划
     */
    private boolean needSplit() {
        boolean result = false;
        switch(direction) {
        case Long:
            //向上笔划, 最高点向下超出阈值, 需要拆分
            result = max.isGreaterThan(close.plus(option.strokeThreshold)) || close.isLessThan(open);
            break;
        case Short:
            //向下笔划, 最低点向上超出阈值, 需要拆分
            result = min.isLessThan(close.minus(option.strokeThreshold)) || close.isGreaterThan(open);
            break;
        case Net:
            break;
        }
        return result;
    }

    /**
     * 拆分出一个与当前笔划方向相反的新笔划
     */
    private SimpleStrokeBar split() {
        SimpleStrokeBar result = null;

        List<Bar2> removedBars = new ArrayList<>();
        switch(direction) {
        case Long:
        {
            //向上笔划, 从最高点拆分, 新笔划向下
            int barCount0 = bars.size();
            int beginIndex = bars.indexOf(barMax)+1;
            for(int i= beginIndex; i<bars.size();i++) {
                removedBars.add(bars.get(i));
            }
            for(int i=bars.size()-1; i>=beginIndex; i--) {
                bars.remove(i);
            }
            int barCount1 = bars.size();
            assert(barCount0==barCount1+removedBars.size() && barCount1==beginIndex);

            barClose = barMax;
            this.close = max;
            this.end = barClose.getEndTime();

            if ( barMin.getEndTime().isAfter(end) ) {
                barMin = min(barOpen, barClose);
            }
            updateVol();
            break;
        }
        case Short:
        {
            //向下笔划, 从最低的拆分, 新笔划向上
            int barCount0 = bars.size();
            int beginIndex = bars.indexOf(barMin)+1;
            for(int i= beginIndex; i<bars.size();i++) {
                removedBars.add(bars.get(i));
            }
            for(int i=bars.size()-1; i>=beginIndex; i--) {
                bars.remove(i);
            }
            int barCount1 = bars.size();
            assert(barCount0==barCount1+removedBars.size() && barCount1==beginIndex);

            barClose = barMin;
            this.close = min;
            this.end = barClose.getEndTime();
            if ( barMax.getEndTime().isAfter(end)) {
                barMax = max(barOpen, barClose);
            }
            updateVol();
            break;
        }
        default:
            break;
        }

        if ( removedBars.size()>0 ) {
            result = new SimpleStrokeBar(option, removedBars, direction.oppose());
        }
        return result;
    }

    private Bar2 min(Bar2 bar0, Bar2 bar1) {
        Num num0 = option.strokeBarPriceGetter.getPrice(bar0);
        Num num1 = option.strokeBarPriceGetter.getPrice(bar1);
        if( num0.isLessThan(num1)) {
            return bar0;
        }
        return bar1;
    }

    private Bar2 max(Bar2 bar0, Bar2 bar1) {
        Num num0 = option.strokeBarPriceGetter.getPrice(bar0);
        Num num1 = option.strokeBarPriceGetter.getPrice(bar1);
        if( num0.isGreaterThan(num1)) {
            return bar0;
        }
        return bar1;
    }

}
