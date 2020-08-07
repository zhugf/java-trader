package trader.service.ta.trend;

import trader.common.exchangeable.ExchangeableTradingTimes;
import trader.service.md.MarketData;
import trader.service.ta.FutureBar;

/**
 * 从Bar直接构建笔划线段
 */
public class SimpleTrendBarBuilder extends StackedTrendBarBuilder {

    public SimpleTrendBarBuilder(WaveBarOption option, ExchangeableTradingTimes tradingTimes) {
        super(option, tradingTimes);
    }

    private FutureBar bar0 = null;

    @Deprecated
    public boolean update(MarketData tick) {
        return super.update(tick);
    }

    public boolean update(FutureBar bar) {
        bar0 = bar;
        return super.update(null);
    }

    @Override
    protected WaveBar updateStroke(MarketData tick) {
        WaveBar result = null;
        FutureBar bar = bar0; bar0 = null;
        if ( bar==null ) {
            return result;
        }
        WaveBar lastStrokeBar = getLastStrokeBar();

        if ( lastStrokeBar==null ) {
            result = new SimpleStrokeBar(0, option, bar);
        }else {
            result = lastStrokeBar.update(null, bar);
        }
        return result;
    }

}
