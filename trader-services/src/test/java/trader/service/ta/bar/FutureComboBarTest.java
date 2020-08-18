package trader.service.ta.bar;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableData;
import trader.common.exchangeable.ExchangeableTradingTimes;
import trader.common.exchangeable.ExchangeableType;
import trader.common.tick.PriceLevel;
import trader.common.util.DateUtil;
import trader.common.util.TraderHomeUtil;
import trader.service.TraderHomeHelper;
import trader.service.md.MarketDataService;
import trader.service.ta.BarSeriesLoader;
import trader.service.util.SimpleBeansContainer;
import trader.simulator.SimMarketDataService;

public class FutureComboBarTest {

    static {
        TraderHomeHelper.init(null);
    }

    @Test
    public void testCombo1() throws Exception
    {
        Exchangeable e = Exchangeable.fromString("SPD AP010&AP101");
        assertTrue( e.getType()==ExchangeableType.FUTURE_COMBO);
        ExchangeableTradingTimes tradingTimes = e.exchange().getTradingTimes(e, DateUtil.str2localdate("20200803"));
        FutureComboBarBuilder barBuilder = new FutureComboBarBuilder(tradingTimes, PriceLevel.MIN5);
        SimpleBeansContainer beansContainer = new SimpleBeansContainer();
        final SimMarketDataService mdService = new SimMarketDataService();
        mdService.init(beansContainer);
        beansContainer.addBean(MarketDataService.class, mdService);

        ExchangeableData data = TraderHomeUtil.getExchangeableData();
        BarSeriesLoader loader= new BarSeriesLoader(beansContainer, data);
        loader.setStartTradingDay(DateUtil.str2localdate("20200803"));
        loader.setEndTradingDay(DateUtil.str2localdate("20200804"));
        barBuilder.loadHistoryData(loader);
        assertTrue(barBuilder.getLevel()==PriceLevel.MIN5);
        assertTrue(barBuilder.getTimeSeries(PriceLevel.MIN5).getBarCount()==45*2);
    }
}
