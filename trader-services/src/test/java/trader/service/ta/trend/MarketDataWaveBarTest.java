package trader.service.ta.trend;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.time.LocalDate;
import java.time.Month;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import trader.common.exchangeable.Exchange;
import trader.common.exchangeable.ExchangeableData;
import trader.common.exchangeable.Future;
import trader.common.exchangeable.MarketDayUtil;
import trader.common.tick.PriceLevel;
import trader.common.util.PriceUtil;
import trader.common.util.TraderHomeUtil;
import trader.service.TraderHomeHelper;
import trader.service.md.MarketData;
import trader.service.md.MarketDataService;
import trader.service.ta.LongNum;
import trader.service.ta.TimeSeriesLoader;
import trader.service.ta.trend.WaveBar.WaveType;
import trader.service.trade.TradeConstants.PosDirection;
import trader.simulator.SimBeansContainer;
import trader.simulator.SimMarketDataService;

@SuppressWarnings({"rawtypes","unchecked"})
public class MarketDataWaveBarTest {

    @Before
    public void setup() {
        TraderHomeHelper.init();
    }

    static long tickStep;
    static int tickCount;

    @Test
    public void testSectionBarFromCtpTick_au1906() throws Exception
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
        tickStep = PriceUtil.price2long(0.05);
        tickCount = 3;
        LocalDate date = LocalDate.of(2018, 12, 2);
        while(true) {
            if ( MarketDayUtil.isMarketDay(Exchange.SHFE, date)) {
                loadTickData(loader.loadMarketDataTicks(date, ExchangeableData.TICK_CTP));
            }
            date = date.plusDays(1);
            if ( date.getMonth()!=Month.DECEMBER) {
                break;
            }
        }
    }

    @Test
    public void testSectionBarFromCtpTick_ru1901() throws Exception
    {
        SimBeansContainer beansContainer = new SimBeansContainer();
        final SimMarketDataService mdService = new SimMarketDataService();
        mdService.init(beansContainer);
        beansContainer.addBean(MarketDataService.class, mdService);

        Future ru1901 = Future.fromInstrument("ru1901");
        ExchangeableData data = TraderHomeUtil.getExchangeableData();
        TimeSeriesLoader loader= new TimeSeriesLoader(beansContainer, data);
        loader
            .setExchangeable(ru1901)
            .setStartTradingDay(LocalDate.of(2018, 12, 13))
            .setEndTradingDay(LocalDate.of(2018, 12, 13))
            .setLevel(PriceLevel.TICKET);
        tickStep = PriceUtil.price2long(5);
        tickCount = 3;
        LocalDate date = LocalDate.of(2018, 12, 2);
        while(true) {
            if ( MarketDayUtil.isMarketDay(Exchange.SHFE, date)) {
                if ( data.exists(ru1901, ExchangeableData.TICK_CTP, date) ) {
                    loadTickData(loader.loadMarketDataTicks(date, ExchangeableData.TICK_CTP));
                }
            }
            date = date.plusDays(1);
            if ( date.getMonth()!=Month.DECEMBER) {
                break;
            }
        }
    }

    private static void loadTickData(List<MarketData> mds)
    {
        WaveBarBuilder builder = new WaveBarBuilder();

        builder.setNumFunction(LongNum::valueOf).setStrokeDirectionThreshold(new LongNum(tickStep*tickCount));
        for(MarketData md:mds) {
            builder.onMarketData(md);
        }
        List<WaveBar> strokeBars = builder.getBars(WaveType.Stroke);
        assertTrue(strokeBars!=null);
        PosDirection lastStrokeDir = null;
        for(int i=0;i<strokeBars.size();i++) {
            WaveBar strokeBar = strokeBars.get(i);
            if (strokeBar.getDirection()==PosDirection.Long) {
                assertTrue(strokeBar.getOpenPrice().isLessThan(strokeBar.getClosePrice()));
            } else if (strokeBar.getDirection()==PosDirection.Short) {
                assertTrue(strokeBar.getOpenPrice().isGreaterThan(strokeBar.getClosePrice()));
            }
            if ( i>0 ) {
                if (strokeBar.getDirection()==PosDirection.Long) {
                    assertTrue(lastStrokeDir==PosDirection.Short);
                } else if (strokeBar.getDirection()==PosDirection.Short) {
                    assertTrue(lastStrokeDir==PosDirection.Long);
                }else {
                    fail();
                }
            }
            lastStrokeDir = strokeBar.getDirection();
        }
        List<WaveBar> sectionBars = builder.getBars(WaveType.Section);
        assertTrue(sectionBars!=null);
        WaveBar prevSectionBar = null;
        for(int i=0;i<sectionBars.size();i++) {
            WaveBar sectionBar = sectionBars.get(i);
            if (sectionBar.getDirection()==PosDirection.Long) {
                assertTrue(sectionBar.getOpenPrice().isLessThan(sectionBar.getClosePrice()));
            }else if (sectionBar.getDirection()==PosDirection.Short) {
                assertTrue(sectionBar.getOpenPrice().isGreaterThan(sectionBar.getClosePrice()));
            }else {
                fail();
            }

            if ( i>0 ) {
                if (sectionBar.getDirection()==PosDirection.Long) {
                    assertTrue(prevSectionBar.getDirection()==PosDirection.Short);
                }else if (sectionBar.getDirection()==PosDirection.Short) {
                    assertTrue(prevSectionBar.getDirection()==PosDirection.Long);
                }else {
                    fail();
                    break;
                }
            }
            prevSectionBar = sectionBar;
            //检查线段和笔划的时间长度
            long sectionSeconds = sectionBar.getTimePeriod().toSeconds();
            long strokeSeconds = 0;
            for(WaveBar bar:(List<WaveBar>)sectionBar.getBars()) {
                strokeSeconds+= bar.getTimePeriod().toSeconds();
            }
            assertTrue( Math.abs(sectionSeconds-strokeSeconds)<=(sectionBar.getBars().size()) );
        }

        System.out.println("---- SECTION DUMP ----");
        for(int i=0;i<sectionBars.size();i++) {
            WaveBar sectionBar = sectionBars.get(i);
            System.out.println(sectionBar);
            for(Object strokeBar:sectionBar.getBars()) {
                System.out.println("\t"+strokeBar);
            }
        }
    }
}
