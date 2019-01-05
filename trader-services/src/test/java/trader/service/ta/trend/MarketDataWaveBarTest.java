package trader.service.ta.trend;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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
import trader.service.trade.TradeConstants.PosDirection;
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

        List<MarketData> mds = loader.loadMarketDataTicks(LocalDate.of(2018, 12, 27), ExchangeableData.TICK_CTP);

        MarketDataWaveBarBuilder builder = new MarketDataWaveBarBuilder();

        long auTickStep = PriceUtil.price2long(0.05);
        int tickCount = 2;
        builder.setNumFunction(LongNum::valueOf).setStrokeDirectionThreshold(new LongNum(auTickStep*tickCount));
        for(MarketData md:mds) {
            builder.onMarketData(md);
        }
        List<WaveBar> strokeBars = builder.getBars(WaveType.Stroke);
        assertTrue(strokeBars!=null);
        PosDirection lastStrokeDir = null;
        for(int i=0;i<strokeBars.size();i++) {
            WaveBar strokeBar = strokeBars.get(i);
            switch(strokeBar.getDirection()) {
            case Long:
                assertTrue(strokeBar.getOpenPrice().isLessThan(strokeBar.getClosePrice()));
                break;
            case Short:
                assertTrue(strokeBar.getOpenPrice().isGreaterThan(strokeBar.getClosePrice()));
                break;
            default:
                break;
            }
            if ( i>0 ) {
                switch(strokeBar.getDirection()) {
                case Long:
                    assertTrue(lastStrokeDir==PosDirection.Short);
                    break;
                case Short:
                    assertTrue(lastStrokeDir==PosDirection.Long);
                    break;
                default:
                    fail();
                    break;
                }
            }
            lastStrokeDir = strokeBar.getDirection();
        }
        List<WaveBar> sectionBars = builder.getBars(WaveType.Section);
        assertTrue(sectionBars!=null);
        PosDirection lastSectionDir = null;
        for(int i=0;i<sectionBars.size();i++) {
            WaveBar sectionBar = sectionBars.get(i);
            switch(sectionBar.getDirection()) {
            case Long:
                assertTrue(sectionBar.getOpenPrice().isLessThan(sectionBar.getClosePrice()));
                break;
            case Short:
                assertTrue(sectionBar.getOpenPrice().isGreaterThan(sectionBar.getClosePrice()));
                break;
            default:
                fail();
                break;
            }

            if ( i>0 ) {
                switch(sectionBar.getDirection()) {
                case Long:
                    assertTrue(lastSectionDir==PosDirection.Short);
                    break;
                case Short:
                    assertTrue(lastSectionDir==PosDirection.Long);
                    break;
                default:
                    fail();
                    break;
                }
            }
            lastSectionDir = sectionBar.getDirection();
        }
    }

}
