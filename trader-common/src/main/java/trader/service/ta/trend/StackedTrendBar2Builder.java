package trader.service.ta.trend;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import trader.common.exchangeable.ExchangeableTradingTimes;
import trader.service.md.MarketData;
import trader.service.ta.Bar2;
import trader.service.ta.LeveledTimeSeries;
import trader.service.ta.bar.FutureBarBuilder;

/**
 * 从TICK实时生成Bar2, 再创建: 笔划/线段
 */
public class StackedTrendBar2Builder extends StackedTrendBarBuilder {
    private static final Logger logger = LoggerFactory.getLogger(StackedTrendBarBuilder.class);

    private FutureBarBuilder barBuilder;

    public StackedTrendBar2Builder(WaveBarOption option, ExchangeableTradingTimes tradingTimes, FutureBarBuilder barBuilder) {
        super(tradingTimes);
        this.option = option;
        this.barBuilder = barBuilder;
        //添加已有的Bar, 重建走势
        LeveledTimeSeries series = barBuilder.getTimeSeries(barBuilder.getLevel());
        for(int i=0;i<series.getBarCount();i++) {
            Bar2 bar = series.getBar2(i);
            WaveBar<Bar2> newStroke = updateStroke(bar);
            if ( newStroke!=null ) {
                updateSection(newStroke);
            }
        }
    }

    public FutureBarBuilder getBarBuilder() {
        return barBuilder;
    }

    @Override
    protected WaveBar<Bar2> updateStroke(MarketData tick) {
        WaveBar<Bar2> result = null;
        Bar2 bar = null;
        if ( barBuilder.update(tick) ) {
            LeveledTimeSeries series = barBuilder.getTimeSeries(barBuilder.getLevel());
            if ( series.getBarCount()>=2 ) {
                //只获取已结束的Bar
                bar = (Bar2)series.getBar(series.getBarCount()-2);
            }
        }
        if ( bar!=null ) {
            result = updateStroke(bar);
        }
        return result;
    }

    protected WaveBar<Bar2> updateStroke(Bar2 bar) {
        WaveBar<Bar2> result = null;
        WaveBar<Bar2> lastStrokeBar = getLastStrokeBar();
        if ( lastStrokeBar==null ) {
            result = new SimpleStrokeBar(0, getOption(), bar);
        }else {
            result = lastStrokeBar.update(null, bar);
        }
        if( result!=null ) {
            strokeSeries.addBar(result);
        }
        return result;
    }

}
