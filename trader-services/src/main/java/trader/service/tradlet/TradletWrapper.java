package trader.service.tradlet;

import trader.common.beans.BeansContainer;
import trader.service.md.MarketData;
import trader.service.ta.LeveledTimeSeries;

/**
 * 可动态切换实际实现类的策略包装类
 */
public class TradletWrapper implements Tradlet {
    private BeansContainer beansContainer;
    private Tradlet delegate;

    public TradletWrapper(Tradlet tactic) {
        this.delegate = tactic;
    }

    @Override
    public void init(BeansContainer beansContainer) throws Exception {
        this.beansContainer = beansContainer;
        delegate.init(beansContainer);
    }

    @Override
    public void destroy() {
        beansContainer = null;
        delegate.destroy();
    }

    @Override
    public TradletMetadata getMetadata() {
        return delegate.getMetadata();
    }

    public void setDelegate(Tradlet tradlet) {
        this.delegate = tradlet;
    }

    @Override
    public void onTick(MarketData marketData) {
        delegate.onTick(marketData);
    }

    @Override
    public void onNewBar(LeveledTimeSeries series) {
        delegate.onNewBar(series);
    }

    @Override
    public void onNoopSecond() {
        delegate.onNoopSecond();
    }

}
