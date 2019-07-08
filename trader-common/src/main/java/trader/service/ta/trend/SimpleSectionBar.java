package trader.service.ta.trend;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.num.Num;

import trader.common.exchangeable.Exchangeable;
import trader.common.util.CollectionUtil;
import trader.common.util.DateUtil;
import trader.service.md.MarketData;
import trader.service.ta.LongNum;
import trader.service.trade.TradeConstants.PosDirection;

/**
 * 曲折线段, 但是有着相同方向.
 * 简单实现, 要求最后一个相同方向的笔划不能低于上一个相同方向的笔划.
 */
public class SimpleSectionBar extends WaveBar<WaveBar> {
    private static final long serialVersionUID = 358487162488223434L;
    private static final Logger logger = LoggerFactory.getLogger(SimpleSectionBar.class);

    protected boolean canMerge;
    protected List<WaveBar> bars;
    protected LinkedList<WaveBar> charBars;
    protected SimpleSectionBar mergedTo;

    public SimpleSectionBar(List<WaveBar> strokeBars) {
        WaveBar stroke1 = strokeBars.get(0);
        WaveBar strokeN = strokeBars.get(strokeBars.size()-1);
        assert(stroke1.getDirection()==strokeN.getDirection());
        bars = new ArrayList<>(strokeBars);
        charBars = new LinkedList<>();
        direction = stroke1.getDirection();
        recompute();
        for(WaveBar stroke:strokeBars) {
            addCharStroke(stroke);
        }
        assert(open!=null);
    }

    @Override
    public WaveType getWaveType() {
        return WaveType.Section;
    }

    @Override
    public List<WaveBar> getBars() {
        return Collections.unmodifiableList(bars);
    }

    @Override
    public int getBarCount() {
        return bars.size();
    }

    @Override
    public MarketData getOpenTick() {
        return bars.get(0).getOpenTick();
    }

    @Override
    public MarketData getCloseTick() {
        return bars.get(bars.size()-1).getCloseTick();
    }

    @Override
    public MarketData getMaxTick() {
        return null;
    }

    @Override
    public MarketData getMinTick() {
        return null;
    }

    /**
     * 当最后一个笔划更新或拆分后, 后检查线段是否被破坏
     */
    @Override
    public WaveBar<WaveBar> update(WaveBar prevSection, WaveBar stroke) {
        canMerge = false;
        boolean newStroke = false; //是否是已有笔划或新笔划
        if ( bars.lastIndexOf(stroke)<0 ) {
            bars.add(stroke);
            newStroke = true;
        }
        //同向笔划更新, 不需要检查新的线段创建
        if ( stroke.getDirection()==getDirection() ) {
            recompute();
            return null;
        } else {
            //反向笔划, 判断是否需要重新生成特征序列
            if ( newStroke ) {
                addCharStroke(stroke);
            } else if ( needRebuildcharBars(stroke) ) {
                rebuildcharBars();
            }
        }

        WaveBar breakStroke = null;
        if ( (breakStroke = checkSectionBreak1())!=null ){
            //check if the section needs to merge back
            if ( needsMergeBack(prevSection) ){
                canMerge = true;
                return null;
            }
            return sectionBreak(breakStroke);
        }
        if ( (breakStroke = checkSectionBreak2())!=null ){
            return sectionBreak(breakStroke);
        }
        if( breakStroke==null ) {
            recompute();
        }
        return null;
    }

    @Override
    public Exchangeable getExchangeable() {
        return bars.get(0).getExchangeable();
    }

    @Override
    public boolean canMerge() {
        return canMerge;
    }

    @Override
    public Duration getTimePeriod() {
        Duration result = null;
        for(WaveBar bar:bars) {
            if ( result==null) {
                result = bar.getTimePeriod();
            }else {
                result = result.plus(bar.getTimePeriod());
            }
        }
        return result;
    }

    @Override
    public void merge(WaveBar sectionToMerge){
        WaveBar lastStroke = null;
        for(Object strokeToMerge: sectionToMerge.getBars()){
            WaveBar stroke0 = (WaveBar)strokeToMerge;
            bars.add(stroke0);
            addCharStroke(stroke0);
            lastStroke = stroke0;
        }
        recompute();
        if ( sectionToMerge instanceof SimpleSectionBar ) {
            ((SimpleSectionBar)sectionToMerge).setMergedTo(this);
        }
    }

    public SimpleSectionBar getMergedTo() {
        return mergedTo;
    }

    public void setMergedTo(SimpleSectionBar mergedTo) {
        this.mergedTo = mergedTo;
        bars.clear();
        open = LongNum.ZERO;
        close = LongNum.ZERO;
        max = LongNum.ZERO;
        min = LongNum.ZERO;
        volume = LongNum.ZERO;
        amount = LongNum.ZERO;
        direction = PosDirection.Net;
    }

    /**
     * 更新数据: OHLC, VA
     */
    private void recompute() {
        WaveBar stroke1 = bars.get(0);
        WaveBar strokeN = bars.get(bars.size()-1);

        begin = stroke1.getBeginTime();
        open = stroke1.getOpenPrice();

        end = strokeN.getEndTime();
        close = strokeN.getClosePrice();
        max = stroke1.getMaxPrice();
        min = stroke1.getMinPrice();
        Num volume = LongNum.ZERO;
        Num amount = LongNum.ZERO;
        for(WaveBar stroke:bars) {
            Num max2 = stroke.getMaxPrice(), min2 = stroke.getMinPrice();
            if ( max2.isGreaterThan(max)) {
                max = max2;
            }
            if( min2.isLessThan(min)) {
                min = min2;
            }
            volume = stroke.getVolume().plus(volume);
            amount = stroke.getAmount().plus(amount);
        }
        this.volume = volume;
        this.amount = amount;
        MarketData mdOpen = getOpenTick(), mdClose = getCloseTick();
        long vol = 0;
        if ( mdOpen!=null ) {
            vol = mdClose.volume-mdOpen.volume;
        }
        if ( vol==0 ) {
            avgPrice = LongNum.fromRawValue(mdClose.averagePrice);
        }else {
            avgPrice = LongNum.fromRawValue( (mdClose.turnover - mdOpen.turnover)/vol );
        }
        openInterest = (mdClose.openInterest);
        mktAvgPrice = LongNum.fromRawValue(mdClose.averagePrice);
    }

    /**
     * 增加一个特征序列，同时对上一个特征序列进行正规化处理
     */
    private void addCharStroke(WaveBar charStroke){
        if ( charStroke.getDirection()==getDirection() ) {
            return;
        }
        if ( !charBars.isEmpty() && charStroke==charBars.getLast() ) {
            return;
        }
        if( charBars.size()>=2 ){
            WaveBar toCanonical = charBars.get(charBars.size()-1);
            WaveBar stroke2 = charBars.get(charBars.size()-2);
            assert(toCanonical.direction==stroke2.direction);
            assert(toCanonical.direction==charStroke.direction);
            if ( WaveBar.barContains(stroke2, toCanonical) || WaveBar.barContains(toCanonical, stroke2) ){
                charBars.removeLast();
                charBars.removeLast();
                charBars.add(new CompositeStrokeBar(stroke2, toCanonical));
            }
        }
        charBars.add(charStroke);
    }

    /**
     * 判断是否需要重新构建特征序列
     */
    private boolean needRebuildcharBars(WaveBar lastStroke) {
        WaveBar prevCharBar = null;
        WaveBar charStroke = charBars.getLast();
        boolean result = false;
        if ( charStroke!=lastStroke ) {
            //如果最后一个特征Bar是复合笔划, 需要重构特征序列
            result = true;
        } else {
            if ( charBars.size()>=2 ) {
                prevCharBar = charBars.get(charBars.size()-2);
                //如果笔N与笔N-1包含, 需要重构特征序列
                if ( WaveBar.barContains2(prevCharBar, lastStroke) ) {
                    result = true;
                }
            }
        }
        return result;
    }

    /**
     * 重构特征序列
     */
    private void rebuildcharBars() {
        charBars.clear();
        for(WaveBar stroke:bars) {
            addCharStroke(stroke);
        }
    }

    /**
     * 单个笔划超过线段起始位置，被破坏
     */
    private WaveBar checkSectionBreak1(){
        WaveBar lastStroke = bars.get(bars.size()-1);
        boolean needBreak = false;
        if ( getDirection()==PosDirection.Long ){
            if( lastStroke.getClosePrice().isLessThan(getOpenPrice()) ){
                needBreak = true;
            }
        }else{
            if ( lastStroke.getClosePrice().isGreaterThan( getOpenPrice()) ){
                needBreak = true;
            }
        }
        if ( !needBreak ){
            return null;
        }
        WaveBar result = lastStroke;
        if ( charBars.size()>=3 && lastStroke==charBars.getLast() ){
            WaveBar lastStroke2 = charBars.get(charBars.size()-2);
            if ( lastStroke.getDirection()==PosDirection.Long && lastStroke2.getOpenPrice().isLessThan(lastStroke.getOpenPrice())){
                result = lastStroke2;
            }
            if ( lastStroke.getDirection()==PosDirection.Short && lastStroke2.getOpenPrice().isGreaterThan(lastStroke2.getOpenPrice())){
                result = lastStroke2;
            }
        }
        if ( logger.isDebugEnabled() ) {
            logger.debug("Section "+this+" #1 breaks at "+result);
        }
        return result;
    }

    /**
     * 线段破坏
     *
     * http://www.360doc.com/content/11/0717/14/637236_134086479.shtml
     * http://wenku.baidu.com/link?url=gUsGtomPrnPZ36XzQRpRLHEkeKc999cA5miZH0L5uCGdoceaxMDPDR2VwMPDKUUK4OefW6om9IOLsbfSozbE4bCXn9eEAkxetgdLq96k7xy
     * http://blog.sina.com.cn/s/blog_713363600100niiq.html
     */
    private WaveBar checkSectionBreak2(){
        //TODO 是否一定要>=3？
        if ( charBars.size()<3 ) {
            return null;
        }
        WaveBar strokeN = charBars.getLast();
        WaveBar strokeN_1 = charBars.get(charBars.size()-2);
        if ( (WaveBar.barContains(strokeN_1,strokeN) || WaveBar.barContains(strokeN,strokeN_1)) && charBars.size()==3 ) {
            return null;
        }
        WaveBar strokeN_2 = charBars.get(charBars.size()-3);

        if ( getDirection()==PosDirection.Long ){
            assert(strokeN.getDirection()==PosDirection.Short);
            assert(strokeN_1.getDirection()==PosDirection.Short);
            assert(strokeN_2.getDirection()==PosDirection.Short);
            assert(strokeN.getOpenPrice().isGreaterThanOrEqual(strokeN.getClosePrice()) );
            assert(strokeN_1.getOpenPrice().isGreaterThanOrEqual(strokeN_1.getClosePrice()));
            assert(strokeN_2.getOpenPrice().isGreaterThanOrEqual(strokeN_2.getClosePrice()));
            //继续向上

            //正常: 形成顶底分型, 且第一第二笔划之间有重叠
            if ( WaveBar.barTopSeq(strokeN_2, strokeN_1, strokeN)
                    && (WaveBar.barOverlap(strokeN_2, strokeN_1) || WaveBar.barOverlap(strokeN_2, strokeN)) ){
                if ( logger.isDebugEnabled() ) {
                    logger.debug("Section "+this+" #2 breaks at "+strokeN_1);
                }
                return strokeN_1;
            }
            //非顶底分型，检测是否是依次向下的序列
            if ( strokeN_2.getOpenPrice().isGreaterThan(strokeN_1.getOpenPrice()) && strokeN_1.getOpenPrice().isGreaterThan(strokeN.getOpenPrice()) ){
                if ( logger.isDebugEnabled() ) {
                    logger.debug("Section "+this+" #2 breaks at "+strokeN_2);
                }
                return strokeN_2;
            }
        }else{
            assert(strokeN.getDirection()==PosDirection.Long);
            assert(strokeN_1.getDirection()==PosDirection.Long);
            assert(strokeN_2.getDirection()==PosDirection.Long);
            assert(strokeN.getOpenPrice().isLessThanOrEqual(strokeN.getClosePrice()));
            assert(strokeN_1.getOpenPrice().isLessThanOrEqual(strokeN_1.getClosePrice()));
            assert(strokeN_2.getOpenPrice().isLessThanOrEqual(strokeN_2.getClosePrice()));
            //继续向下
            if ( strokeN.end.compareTo(strokeN_1.end)<0 ) {
                return null;
            }
            //正常: 形成顶底分型, 且第一第二笔划之间有重叠
            if ( strokeBottomSeq(strokeN_2, strokeN_1, strokeN)
                    && (WaveBar.barOverlap(strokeN_2, strokeN_1) || WaveBar.barOverlap(strokeN_2, strokeN)) ){
                if ( logger.isDebugEnabled() ) {
                    logger.debug("Section "+this+" #2 breaks at "+strokeN_1);
                }
                return strokeN_1;
            }
            //非顶底分型，检测是否是依次向上的序列
            if ( strokeN_2.getOpenPrice().isLessThan(strokeN_1.getOpenPrice()) && strokeN_1.getOpenPrice().isLessThan(strokeN.getOpenPrice()) ){
                if ( logger.isDebugEnabled() ) {
                    logger.debug("Section "+this+" #2 breaks at "+strokeN_2);
                }
                return strokeN_2;
            }
        }
        return null;
    }

    /**
     * 创建新的线段
     */
    private SimpleSectionBar sectionBreak(WaveBar breakStroke){
        WaveBar lastBreakStroke = getLastStroke(breakStroke);
        assert(lastBreakStroke.direction!=direction);
        LinkedList<WaveBar> newStrokes = new LinkedList<>();
        CollectionUtil.<WaveBar>moveAllAfter(bars, lastBreakStroke, true, newStrokes);
        {
            //重新生成特征序列
            charBars.clear();
            for(WaveBar stroke:bars){
                addCharStroke(stroke);
            }
        }
        assert(bars.size()>0);
        WaveBar lastStroke = bars.get(bars.size()-1);
        assert(lastStroke.direction==direction);
        end = lastStroke.end;
        recompute();
        assert(newStrokes.size()>0);
        return new SimpleSectionBar(newStrokes);
    }

    /**
     * 从复合笔划中找到真正的最后一笔
     */
    private WaveBar getLastStroke(WaveBar stroke){
        if ( stroke instanceof CompositeStrokeBar ){
            List<WaveBar> bars = ((CompositeStrokeBar)stroke).getBars();
            return getLastStroke( bars.get(bars.size()-1) );
        }else{
            return stroke;
        }
    }

    /**
     * 当出现一根笔划破坏当前线段时, 是否需要合并当前线段到上一个线段
     * <BR>1 当前线段high-low < 1/2* 上个线段
     * <BR>2 当前线段笔划数<=4
     */
    private boolean needsMergeBack(WaveBar prevSection){
        if ( prevSection==null ){
            return false;
        }
        if ( bars.size()<=2 ){
            return true;
        }
        Num height2 = bars.get(0).getOpenPrice().minus(bars.get(bars.size()-2).getClosePrice()).abs();
        return bars.size()<=4 && height2.isLessThan( prevSection.getMaxPrice().dividedBy(LongNum.valueOf(2)));
    }

    @Override
    public String toString() {
        Duration dur= this.getTimePeriod();
        return "Section[ "+direction+", B "+DateUtil.date2str(begin.toLocalDateTime())+", "+dur.getSeconds()+"S, O "+open+" C "+close+" H "+open.minus(close).abs()+" "+bars.size()+" bars]";
    }

}
