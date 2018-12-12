package trader.service.tradlet.impl;

import trader.common.beans.Discoverable;
import trader.service.md.MarketData;
import trader.service.ta.LeveledTimeSeries;
import trader.service.tradlet.Tradlet;
import trader.service.tradlet.TradletContext;

/**
 * 简单止损策略, 用于开仓后一段时间内止损, 需要配置中明确止损幅度
 */
@Discoverable(interfaceClass = Tradlet.class, purpose = "StopLoss")
public class SimpleStopLossTradlet implements Tradlet {

    @Override
    public void init(TradletContext context) {

    }

    @Override
    public void destroy() {

    }

    @Override
    public void onTick(MarketData marketData) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onNewBar(LeveledTimeSeries series) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onNoopSecond() {
        // TODO Auto-generated method stub

    }

}
