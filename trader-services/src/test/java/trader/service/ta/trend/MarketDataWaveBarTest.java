package trader.service.ta.trend;

import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import trader.common.exchangeable.ExchangeableData;
import trader.common.exchangeable.Future;
import trader.common.tick.PriceLevel;
import trader.common.util.PriceUtil;
import trader.common.util.TraderHomeUtil;
import trader.service.TraderHomeTestUtil;
import trader.service.md.MarketData;
import trader.service.md.MarketDataService;
import trader.service.ta.LongNum;
import trader.service.ta.TimeSeriesLoader;
import trader.service.ta.trend.WaveBar.WaveType;
import trader.simulator.SimBeansContainer;
import trader.simulator.SimMarketDataService;

public class MarketDataWaveBarTest {

    @Before
    public void setup() {
        TraderHomeTestUtil.initRepoistoryDir();
    }

    @Test
    public void testCtpTick() throws Exception
    {
        SimBeansContainer beansContainer = new SimBeansContainer();
        final SimMarketDataService mdService = new SimMarketDataService();
        mdService.init(beansContainer);
        beansContainer.addBean(MarketDataService.class, mdService);

        Future au1906 = Future.fromInstrument("au1906");
        ExchangeableData data = TraderHomeUtil.getExchangeableData();
        TimeSeriesLoader loader= new TimeSeriesLoader(beansContainer, data);
        loader
            .setExchangeable(au1906)
            .setStartTradingDay(LocalDate.of(2018, 12, 13))
            .setEndTradingDay(LocalDate.of(2018, 12, 13))
            .setLevel(PriceLevel.TICKET);

        List<MarketData> mds = loader.loadMarketDataTicks(LocalDate.of(2018, 12, 13), ExchangeableData.TICK_CTP);

        MarketDataWaveBarBuilder builder = new MarketDataWaveBarBuilder();

        long auTickStep = PriceUtil.price2long(0.05);
        int tickCount = 3;
        builder.setNumFunction(LongNum::valueOf).setStrokeDirectionThreshold(new LongNum(auTickStep*tickCount));
        for(MarketData md:mds) {
            builder.onMarketData(md);
        }
        assertTrue(builder.getBars(WaveType.Stroke)!=null);
    }

}
