package trader.service.ta.trend;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.num.Num;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import trader.common.exchangeable.ExchangeableTradingTimes;
import trader.common.tick.PriceLevel;
import trader.common.util.JsonEnabled;
import trader.service.md.MarketData;
import trader.service.ta.BaseLeveledTimeSeries;
import trader.service.ta.LeveledTimeSeries;
import trader.service.ta.LongNum;
import trader.service.ta.bar.BarBuilder;

/**
 * 基于行情Tick数据直接构建: 笔划-线段
 */
@SuppressWarnings("rawtypes")
public class StackedTrendBarBuilder implements BarBuilder, JsonEnabled {
    private static final Logger logger = LoggerFactory.getLogger(StackedTrendBarBuilder.class);

    protected static final int INDEX_STROKE_BAR = 0;
    protected static final int INDEX_SECTION_BAR = 1;

    protected Function<Number, Num> numFunction = LongNum::valueOf;
    protected ExchangeableTradingTimes tradingTimes;
    protected WaveBarOption option;
    protected BaseLeveledTimeSeries strokeSeries;
    protected BaseLeveledTimeSeries sectionSeries;
    protected boolean strokeNewBar;
    protected boolean sectionNewBar;
    protected MarketData lastTick;

    public StackedTrendBarBuilder(ExchangeableTradingTimes tradingTimes) {
        this.tradingTimes = tradingTimes;
        strokeSeries = new BaseLeveledTimeSeries(tradingTimes.getInstrument(), tradingTimes.getInstrument().uniqueId()+" stroke", PriceLevel.STROKE, LongNum::valueOf);
        sectionSeries = new BaseLeveledTimeSeries(tradingTimes.getInstrument(), tradingTimes.getInstrument().uniqueId()+" section", PriceLevel.SECTION, LongNum::valueOf);
    }

    public WaveBarOption getOption() {
        return option;
    }

    public LeveledTimeSeries getTimeSeries(PriceLevel level){
        if ( level==PriceLevel.STROKE) {
            return strokeSeries;
        }else if ( level==PriceLevel.SECTION) {
            return sectionSeries;
        }
        return null;
    }

    public boolean hasNewBar(PriceLevel level) {
        if ( level==PriceLevel.STROKE) {
            return strokeNewBar;
        }else if ( level==PriceLevel.SECTION) {
            return sectionNewBar;
        }
        return false;
    }

    @Override
    public boolean update(MarketData tick) {
        if ( lastTick!=null && lastTick.updateTime.compareTo(tick.updateTime)>=0 && lastTick.volume>=tick.volume ) {
            return false;
        }
        strokeNewBar = false;
        sectionNewBar = false;

        //如果有新的笔划产生
        WaveBar newStroke = updateStroke(tick);
        if(newStroke!=null) {
            strokeNewBar = true;
        }
        sectionNewBar = updateSection(newStroke);
        lastTick = tick;
        return strokeNewBar;
    }

    protected WaveBar updateStroke(MarketData tick) {
        WaveBar result = null;

        WaveBar lastStrokeBar = getLastStrokeBar();
        if ( lastStrokeBar==null ) {
            result = new MarketDataStrokeBar(0, tradingTimes, option, tick);
        }else {
            result = ((WaveBar<MarketData>)lastStrokeBar).update(null, tick);
        }
        if ( result!=null ) {
            strokeSeries.addBar(result);
        }
        return result;
    }

    protected boolean updateSection(WaveBar newStroke) {
        if ( strokeSeries.isEmpty() ) {
            return false;
        }
        WaveBar lastStrokeBar = newStroke;
        WaveBar lastSectionBar = getLastSectionBar();
        WaveBar prevSectionBar = null;

        if ( sectionSeries.getBarCount()>=2 ) {
            prevSectionBar = (WaveBar)sectionSeries.getBar(sectionSeries.getBarCount()-2);
        }

        WaveBar lastSectionBar0 = lastSectionBar;

        //有了新笔划后, 尝试更新线段
        if ( lastSectionBar==null ) {
            if ( strokeSeries.getBarCount()>=3 ) { //三个笔划对应一个线段
                lastSectionBar = createFirstSection((List)strokeSeries.getBarData());
            }
        } else {
            WaveBar newSectionBar = lastSectionBar.update(prevSectionBar, lastStrokeBar);
            if ( newSectionBar!=null ) {
                lastSectionBar = newSectionBar;
            } else if ( (lastSectionBar).canMerge() && prevSectionBar!=null ) {
                //需要合并
                prevSectionBar.merge(lastSectionBar);
                sectionSeries.removeLastBar();
                sectionSeries.removeLastBar();
                lastSectionBar = prevSectionBar;
            }
        }
        //如果有新的线段产生
        boolean result = false;
        if (lastSectionBar0!=lastSectionBar) {
            sectionSeries.addBar(lastSectionBar);
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
        WaveBar strokes[] = getSectionStrokes(strokeBars, strokeIndex);
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
        SimpleSectionBar result = new SimpleSectionBar(0, sectionStrokes);
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

    protected WaveBar getLastStrokeBar() {
        WaveBar result = null;
        if ( !strokeSeries.isEmpty()) {
            result = (WaveBar)strokeSeries.getLastBar();
        }
        return result;
    }

    protected WaveBar getLastSectionBar() {
        WaveBar result = null;
        if ( !sectionSeries.isEmpty()) {
            result = (WaveBar)sectionSeries.getLastBar();
        }
        return result;
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        if ( lastTick!=null ) {
            json.add("lastTick", lastTick.toJson());
        }
        {
            JsonObject optionsJson = new JsonObject();
            optionsJson.addProperty("strokeThreshold", option.strokeThreshold.toString());
            json.add("options", optionsJson);
        }
        {
            JsonArray array = new JsonArray();
            for(int i=0;i<strokeSeries.getBarCount();i++) {
                WaveBar stroke = (WaveBar)strokeSeries.getBar(i);
                array.add(stroke.toJson());
            }
            json.add("strokes", array);
        }
        {
            JsonArray array = new JsonArray();
            for(int i=0;i<sectionSeries.getBarCount();i++) {
                WaveBar stroke = (WaveBar)sectionSeries.getBar(i);
                array.add(stroke.toJson());
            }
            json.add("sections", array);
        }
        return json;
    }
}
