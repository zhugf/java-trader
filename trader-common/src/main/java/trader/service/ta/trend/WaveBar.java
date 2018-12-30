package trader.service.ta.trend;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;

import org.ta4j.core.Bar;
import org.ta4j.core.num.Num;

import trader.service.trade.TradeConstants.PosDirection;

/**
 * 抽象的缠轮/波浪运动的基本构件: 笔划-线段-中枢和趋势
 */
public abstract class WaveBar implements Bar {
    private static final long serialVersionUID = -8268409694130012911L;

    /**
     * 波浪级别
     */
    public static enum WaveType{
        /**
         * 笔划
         */
        Stroke
        /**
         * 线段
         */
        ,Section
        /**
         * 中枢
         */
        ,Centrum
    }

    protected WaveType waveType;
    protected PosDirection direction;
    protected Num open;
    protected Num close;
    protected Num max;
    protected Num min;
    protected Num volume;
    protected Num amount;
    protected ZonedDateTime begin;
    protected ZonedDateTime end;
    protected List<WaveBar> bars = null;

    @Override
    public Num getOpenPrice() {
        return open;
    }

    @Override
    public Num getMinPrice() {
        return min;
    }

    @Override
    public Num getMaxPrice() {
        return max;
    }

    @Override
    public Num getClosePrice() {
        return close;
    }

    @Override
    public Num getVolume() {
        return volume;
    }

    @Override
    public int getTrades() {
        return 0;
    }

    @Override
    public Num getAmount() {
        return amount;
    }

    @Override
    public Duration getTimePeriod() {
        return Duration.between(begin.toInstant(), end.toInstant());
    }

    @Override
    public ZonedDateTime getBeginTime() {
        return begin;
    }

    @Override
    public ZonedDateTime getEndTime() {
        return end;
    }

    @Override
    public void addTrade(Num tradeVolume, Num tradePrice) {
        throw new UnsupportedOperationException("addTrade is not supported");
    }

    @Override
    public void addPrice(Num price) {
        throw new UnsupportedOperationException("addTrade is not supported");
    }

    /**
     * 方向. Long向上, Short向下, Net不确定
     * <BR>对于部分方向不明的波动, 可能会变
     */
    public PosDirection getDirection() {
        return direction;
    }

    /**
     * 底层构件
     */
    public List<WaveBar> getBars() {
        if ( bars==null ) {
            return Collections.emptyList();
        }
        return bars;
    }

    /**
     * 构件类型
     */
    public abstract WaveType getWaveType();

    /**
     * 是否需要拆分
     */
    public abstract boolean needSplit();

    /**
     * 实际拆分
     */
    public abstract WaveBar split();

}
