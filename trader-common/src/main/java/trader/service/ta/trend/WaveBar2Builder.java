package trader.service.ta.trend;

import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import trader.service.md.MarketData;
import trader.service.ta.Bar2;
import trader.service.ta.LeveledTimeSeries;
import trader.service.ta.bar.FutureBarBuilder;
import trader.service.ta.trend.WaveBar.WaveType;

/**
 * 基于Bar2直接构筑: 笔划/线段
 */
public class WaveBar2Builder extends WaveBarBuilder {
    private static final Logger logger = LoggerFactory.getLogger(WaveBarBuilder.class);

    private static final int INDEX_STROKE_BAR = WaveType.Stroke.ordinal();
    private static final int INDEX_SECTION_BAR = WaveType.Section.ordinal();

    private FutureBarBuilder barBuilder;

    public WaveBar2Builder(FutureBarBuilder barBuilder) {
        this.barBuilder = barBuilder;
        lastBars = new WaveBar[WaveType.values().length];
        bars = new ArrayList[lastBars.length];
        for(int i=0;i<bars.length;i++) {
            bars[i] = new ArrayList<>(1024/(int)Math.pow(2, i));
        }
    }

    public FutureBarBuilder getBarBuilder() {
        return barBuilder;
    }

    @Override
    protected WaveBar updateStroke(MarketData tick) {
        WaveBar result = null;
        Bar2 bar = null;
        if ( barBuilder.update(tick) ) {
            LeveledTimeSeries series = barBuilder.getTimeSeries(barBuilder.getLevel());
            if ( series.getBarCount()>=2 ) {
                //只获取已结束的Bar
                bar = (Bar2)series.getBar(series.getBarCount()-2);
            }
        }
        if ( bar==null ) {
            return result;
        }
        WaveBar lastStrokeBar = lastBars[INDEX_STROKE_BAR];

        if ( lastStrokeBar==null ) {
            result = new SimpleStrokeBar(option, bar);
        }else {
            result = lastStrokeBar.update(null, bar);
        }
        return result;
    }

}
