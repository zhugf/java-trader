package trader.service.ta.trend;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.ta4j.core.num.Num;

import trader.service.md.MarketData;
import trader.service.md.MarketDataListener;
import trader.service.ta.trend.WaveBar.WaveType;

/**
 * 基于行情切片波浪数据直接构建: 分笔-笔划-线段
 */
public class MarketDataWaveBarBuilder implements MarketDataListener {
    private static final int INDEX_STROKE_BAR = WaveType.Stroke.ordinal();

    private Num strokeDirectionThreshold;
    private List<WaveBar>[] bars;
    private WaveBar[] lastBars;
    private Function<Number, Num> numFunction;

    public MarketDataWaveBarBuilder() {
        lastBars = new WaveBar[WaveType.values().length];
        bars = new ArrayList[lastBars.length];
        for(int i=0;i<bars.length;i++) {
            bars[i] = new ArrayList<>(1024/(int)Math.pow(2, i));
        }
    }

    public MarketDataWaveBarBuilder setNumFunction(Function<Number, Num> numFunction) {
        this.numFunction = numFunction;
        return this;
    }

    public Function<Number, Num> getNumFunction(){
        return numFunction;
    }

    /**
     * 设置价格波动阈值, 低于这个范围会被视为细微波动而忽略.
     */
    public MarketDataWaveBarBuilder setStrokeDirectionThreshold(Num threshold) {
        this.strokeDirectionThreshold = threshold;
        return this;
    }

    public List<WaveBar> getBars(WaveType waveType){
        return bars[waveType.ordinal()];
    }

    public WaveBar getLastBar(WaveType waveType) {
        return lastBars[waveType.ordinal()];
    }

    @Override
    public void onMarketData(MarketData md) {
        List<WaveBar> strokeBars = bars[INDEX_STROKE_BAR];
        WaveBar lastStrokeBar = lastBars[INDEX_STROKE_BAR];
        WaveBar lastStrokeBar0 = lastStrokeBar;
        if ( lastStrokeBar==null ) {
            lastStrokeBar = new MarketDataStrokeBar(strokeDirectionThreshold, md);
        }else {
            ((MarketDataStrokeBar)lastStrokeBar).update(md);
            if ( lastStrokeBar.needSplit() ) {
                lastStrokeBar = lastStrokeBar.split();
            }
        }

        //如果有新的笔划产生
        if(lastStrokeBar!=lastStrokeBar0) {
            strokeBars.add(lastStrokeBar);
            lastBars[INDEX_STROKE_BAR] = lastStrokeBar;
        }
    }

}
