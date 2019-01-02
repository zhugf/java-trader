package trader.service.ta.trend;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.ta4j.core.num.Num;

import trader.common.util.CollectionUtil;
import trader.service.ta.LongNum;
import trader.service.trade.TradeConstants.PosDirection;

/**
 * 曲折线段, 但是有着相同方向.
 * 简单实现, 要求最后一个相同方向的笔划不能低于上一个相同方向的笔划.
 */
public class SimpleSectionBar extends WaveBar<WaveBar> {
    private static final long serialVersionUID = 358487162488223434L;

    protected List<WaveBar> bars;
    protected LinkedList<WaveBar> charBars;

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
    }

    @Override
    public WaveType getWaveType() {
        return WaveType.Section;
    }

    @Override
    public List<WaveBar> getBars() {
        return Collections.unmodifiableList(bars);
    }

    /**
     * 当最后一个笔划更新或拆分后, 后检查线段是否被破坏
     */
    @Override
    public WaveBar<WaveBar> update(WaveBar stroke) {
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
//            if ( options.mergeSection && needsMergeBack(prevSection) ){
//                canMerge = true;
//                return null;
//            }
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
    }

    /**
     * 增加一个特征序列，同时对上一个特征序列进行正规化处理
     */
    private void addCharStroke(WaveBar charStroke){
        if ( charStroke.getDirection()==getDirection() ) {
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
                if ( WaveBar.barContains(prevCharBar, lastStroke) ) {
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
        if ( charBars.size()>=3 && lastStroke==charBars.getLast() ){
            WaveBar lastStroke2 = charBars.get(charBars.size()-2);
            if ( lastStroke.getDirection()==PosDirection.Long && lastStroke2.getOpenPrice().isLessThan(lastStroke.getOpenPrice())){
                return lastStroke2;
            }
            if ( lastStroke.getDirection()==PosDirection.Short && lastStroke2.getOpenPrice().isGreaterThan(lastStroke2.getOpenPrice())){
                return lastStroke2;
            }
        }
        return lastStroke;
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
            assert(strokeN.getOpenPrice().isGreaterThan(strokeN.getClosePrice()) );
            assert(strokeN_1.getOpenPrice().isGreaterThan(strokeN_1.getClosePrice()));
            assert(strokeN_2.getOpenPrice().isGreaterThan(strokeN_2.getClosePrice()));
            //继续向上

            //正常: 形成顶底分型, 且第一第二笔划之间有重叠
            if ( WaveBar.barTopSeq(strokeN_2, strokeN_1, strokeN)
                    && (WaveBar.barOverlap(strokeN_2, strokeN_1) || WaveBar.barOverlap(strokeN_2, strokeN)) ){
                return strokeN_1;
            }
            //非顶底分型，检测是否是依次向下的序列
            if ( strokeN_2.begin.compareTo(strokeN_1.begin)>0 && strokeN_1.begin.compareTo(strokeN.begin)>0 ){
                return strokeN_2;
            }
        }else{
            assert(strokeN.getDirection()==PosDirection.Long);
            assert(strokeN_1.getDirection()==PosDirection.Long);
            assert(strokeN_2.getDirection()==PosDirection.Long);
            assert(strokeN.begin.compareTo(strokeN.end)<0);
            assert(strokeN_1.begin.compareTo(strokeN_1.end)<0);
            assert(strokeN_2.begin.compareTo(strokeN_2.end)<0);
            //继续向下
            if ( strokeN.end.compareTo(strokeN_1.end)<0 ) {
                return null;
            }
            //正常: 形成顶底分型, 且第一第二笔划之间有重叠
            if ( strokeBottomSeq(strokeN_2, strokeN_1, strokeN)
                    && (WaveBar.barOverlap(strokeN_2, strokeN_1) || WaveBar.barOverlap(strokeN_2, strokeN)) ){
                return strokeN_1;
            }
            //非顶底分型，检测是否是依次向上的序列
            if ( strokeN_2.begin.compareTo(strokeN_1.begin)<0 && strokeN_1.begin.compareTo(strokeN.begin)<0 ){
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

}
