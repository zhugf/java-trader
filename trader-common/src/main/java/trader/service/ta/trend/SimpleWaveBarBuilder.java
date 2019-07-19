package trader.service.ta.trend;

import trader.service.md.MarketData;
import trader.service.ta.Bar2;

/**
 * 从Bar直接构建笔划线段
 */
public class SimpleWaveBarBuilder extends WaveBarBuilder {

    private Bar2 bar0 = null;

    @Deprecated
    public boolean update(MarketData tick) {
        return super.update(tick);
    }

    public boolean update(Bar2 bar) {
        bar0 = bar;
        return super.update(null);
    }

    @Override
    protected WaveBar updateStroke(MarketData tick) {
        WaveBar result = null;
        Bar2 bar = bar0; bar0 = null;
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
