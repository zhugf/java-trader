package trader.service.ta.trend;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.num.Num;

import trader.common.tick.PriceLevel;
import trader.service.md.MarketData;
import trader.service.ta.LeveledTimeSeries;
import trader.service.ta.LongNum;
import trader.service.ta.bar.BarBuilder;
import trader.service.ta.trend.WaveBar.WaveType;

/**
 * 基于行情Tick数据直接构建: 笔划-线段
 */
@SuppressWarnings("rawtypes")
public class WaveBarBuilder implements BarBuilder {
    private static final Logger logger = LoggerFactory.getLogger(WaveBarBuilder.class);

    private static final int INDEX_STROKE_BAR = WaveType.Stroke.ordinal();
    private static final int INDEX_SECTION_BAR = WaveType.Section.ordinal();

    protected WaveBarOption option;
    protected List<WaveBar>[] bars;
    protected WaveBar[] lastBars;
    protected Function<Number, Num> numFunction = LongNum::valueOf;

    public WaveBarBuilder() {
        lastBars = new WaveBar[WaveType.values().length];
        bars = new ArrayList[lastBars.length];
        for(int i=0;i<bars.length;i++) {
            bars[i] = new ArrayList<>(1024/(int)Math.pow(2, i));
        }
    }

    public WaveBarBuilder setNumFunction(Function<Number, Num> numFunction) {
        this.numFunction = numFunction;
        return this;
    }

    public Function<Number, Num> getNumFunction(){
        return numFunction;
    }

    public WaveBarOption getOption() {
        if ( option==null ) {
            option = new WaveBarOption();
        }
        return option;
    }

    public List<WaveBar> getBars(WaveType waveType){
        return bars[waveType.ordinal()];
    }

    public WaveBar getLastBar(WaveType waveType) {
        return lastBars[waveType.ordinal()];
    }

    /**
     * TODO 尚未实现
     */
    @Override
    public LeveledTimeSeries getTimeSeries(PriceLevel level) {
        return null;
    }

    @Override
    public boolean update(MarketData tick) {
        List<WaveBar> strokeBars = bars[INDEX_STROKE_BAR];
        //如果有新的笔划产生
        WaveBar newStroke = updateStroke(tick);
        if(newStroke!=null) {
            strokeBars.add(newStroke);
            lastBars[INDEX_STROKE_BAR] = newStroke;
        }
        return updateSection();
    }

    protected WaveBar updateStroke(MarketData tick) {
        WaveBar result = null;

        WaveBar lastStrokeBar = lastBars[INDEX_STROKE_BAR];
        if ( lastStrokeBar==null ) {
            result = new MarketDataStrokeBar(option, tick);
        }else {
            result = ((WaveBar<MarketData>)lastStrokeBar).update(null, tick);
        }
        return result;
    }

    protected boolean updateSection() {
        List<WaveBar> strokeBars = bars[INDEX_STROKE_BAR];
        List<WaveBar> sectionBars = bars[INDEX_SECTION_BAR];
        WaveBar prevSectionBar = null;
        if ( sectionBars.size()>=2 ) {
            prevSectionBar = sectionBars.get(sectionBars.size()-2);
        }

        WaveBar lastStrokeBar = lastBars[INDEX_STROKE_BAR];
        WaveBar lastSectionBar = lastBars[INDEX_SECTION_BAR];
        WaveBar lastSectionBar0 = lastSectionBar;

        //有了新笔划后, 尝试更新线段
        if ( lastSectionBar==null ) {
            if ( strokeBars.size()>=3 ) { //三个笔划对应一个线段
                lastSectionBar = createFirstSection(strokeBars);
            }
        } else {
            WaveBar newSectionBar = lastSectionBar.update(prevSectionBar, lastStrokeBar);
            if ( newSectionBar!=null ) {
                lastSectionBar = newSectionBar;
            } else if ( (lastSectionBar).canMerge() && prevSectionBar!=null ) {
                //需要合并
                prevSectionBar.merge(lastSectionBar);
                sectionBars.remove(lastSectionBar);
                sectionBars.remove(prevSectionBar);
                lastSectionBar = prevSectionBar;
            }
        }
        //如果有新的线段产生
        boolean result = false;
        if (lastSectionBar0!=lastSectionBar) {
            sectionBars.add(lastSectionBar);
            lastBars[INDEX_SECTION_BAR] = lastSectionBar;
            result = true;
        }
        return result;
    }

    /**
     * 创建第一个线段, 有些形态要求:
     * <LI>至少3根笔划
     * <LI>笔划1不可包含笔划2, 且笔划2不可包含笔划3
     */
    protected WaveBar createFirstSection(List<WaveBar> strokeBars){
        int strokeIndex = 0;
        WaveBar strokeN = strokeBars.get(strokeBars.size()-1);
        WaveBar strokes[] =getSectionStrokes(strokeBars, strokeIndex);
        //笔2包含笔1和笔3, 需要从笔2开始
        if ( WaveBar.barContains(strokes[1], strokes[0]) && WaveBar.barContains(strokes[1], strokes[2])) {
            strokes = getSectionStrokes(strokeBars, ++strokeIndex);
        }
        //笔1包含笔2, 笔2包含笔3
        if ( strokes!=null && WaveBar.barContains(strokes[0], strokes[1]) && WaveBar.barContains(strokes[1], strokes[2])) {
            //笔3包含笔N, 说明没有走出方向
            if ( strokeN!=strokes[2] && WaveBar.barContains(strokes[2], strokeN)) {
                return null;
            }
        }
        //低于最少笔划
        if ( strokes==null ) {
            return null;
        }
        if ( strokes[0].getDirection()!=strokeN.getDirection() ) {
            return null;
        }
        //创建线段
        List<WaveBar> sectionStrokes = new ArrayList<>();
        for(int i=strokeIndex; i<strokeBars.size(); i++) {
            sectionStrokes.add(strokeBars.get(i));
        }
        SimpleSectionBar result = new SimpleSectionBar(sectionStrokes);
        if ( logger.isDebugEnabled() ) {
            logger.debug("Creates first section "+result);
        }
        return result;
    }

    protected static WaveBar[] getSectionStrokes(List<WaveBar> strokeBars, int strokeIndex){
        if ( strokeBars.size()<(strokeIndex+3)) {
            return null;
        }
        WaveBar[] result = new WaveBar[3];
        result[0] = strokeBars.get(strokeIndex);
        result[1] = strokeBars.get(strokeIndex+1);
        result[2] = strokeBars.get(strokeIndex+2);
        return result;
    }
}
