package trader.service.ta.trend;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;

import org.ta4j.core.num.Num;

import trader.common.exchangeable.Exchangeable;
import trader.service.ta.Bar2;
import trader.service.trade.TradeConstants.PosDirection;

/**
 * 抽象的缠轮/波浪运动的基本构件: 笔划-线段-中枢和趋势
 */
public abstract class WaveBar<T> implements Bar2 {
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

    protected int index;
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
    protected Num avgPrice;
    protected long openInterest;
    protected Num mktAvgPrice;

    public int getIndex() {
        return index;
    }

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
    public long getOpenInterest() {
        return openInterest;
    }

    @Override
    public Num getMktAvgPrice() {
        return mktAvgPrice;
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
    public abstract Duration getTimePeriod();

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
        return Collections.emptyList();
    }

    public int getBarCount() {
        return 0;
    }

    public abstract Exchangeable getExchangeable();

    /**
     * 构件类型
     */
    public abstract WaveType getWaveType();

    /**
     * 更新底层数据
     *
     * @return 是否拆分出新的同级Bar
     */
    public abstract WaveBar update(WaveBar<T> prev, T base);

    /**
     * 可以合并
     */
    public abstract boolean canMerge();

    /**
     * 合并
     */
    public abstract void merge(WaveBar bar);

    @Override
    public Num getAvgPrice() {
        return avgPrice;
    }

    /**
     * 笔1包含笔2
     */
    protected static boolean barContains(WaveBar stroke1, WaveBar stroke2){
        Num high1 = stroke1.getOpenPrice().max(stroke1.getClosePrice());
        Num low1 = stroke1.getOpenPrice().min(stroke1.getClosePrice());
        Num high2 = stroke2.getOpenPrice().max(stroke2.getClosePrice());
        Num low2 = stroke2.getOpenPrice().min(stroke2.getClosePrice());

        if ( (high1.compareTo(high2)>=0 && low1.compareTo(low2)<=0) ){
            return true;
        }
        return false;
    }

    /**
     * 笔1包含笔2, 或笔2包含笔1
     */
    protected static boolean barContains2(WaveBar stroke1, WaveBar stroke2){
        Num high1 = stroke1.getOpenPrice().max(stroke1.getClosePrice());
        Num low1 = stroke1.getOpenPrice().min(stroke1.getClosePrice());
        Num high2 = stroke2.getOpenPrice().max(stroke2.getClosePrice());
        Num low2 = stroke2.getOpenPrice().min(stroke2.getClosePrice());

        if ( (high1.compareTo(high2)>=0 && low1.compareTo(low2)<=0) ){
            return true;
        }
        if ( high2.compareTo(high1)>=0 && low2.compareTo(low1)<=0 ){
            return true;
        }
        return false;
    }

    /**
     * 顶分型
     */
    protected static boolean barTopSeq(WaveBar stroke1, WaveBar stroke2, WaveBar stroke3){
        Num high1 = stroke1.getOpenPrice().max(stroke1.getOpenPrice());
        Num high2 =stroke2.getOpenPrice().max(stroke2.getClosePrice());
        Num high3 =stroke2.getOpenPrice().max(stroke3.getClosePrice());

        return ( high1.isLessThan(high2) && high3.isLessThan(high2) );
    }

    /**
     * 底分型
     */
    protected static boolean strokeBottomSeq(WaveBar stroke1, WaveBar stroke2, WaveBar stroke3){
        Num low1 = stroke1.getOpenPrice().min(stroke1.getOpenPrice());
        Num low2 = stroke2.getOpenPrice().min(stroke2.getOpenPrice());
        Num low3 = stroke3.getOpenPrice().min(stroke3.getOpenPrice());

        return ( low1.isGreaterThan(low2) && low3.isGreaterThan(low2));
    }

    /**
     * 两个笔划有重合
     */
    protected static boolean barOverlap(WaveBar stroke1, WaveBar stroke2){
        assert(stroke1.getDirection()==stroke2.getDirection());
        Num totalV = stroke1.getOpenPrice().minus(stroke2.getClosePrice()).abs();
        Num v1 = stroke1.getOpenPrice().minus(stroke1.getClosePrice()).abs();
        Num v2 = stroke2.getOpenPrice().minus(stroke2.getClosePrice()).abs();
        //totalV < v1+v2
        return totalV.isLessThan( v1.plus(v2) );
    }

}
