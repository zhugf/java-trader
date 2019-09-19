package trader.service.ta.trend;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.time.LocalDate;
import java.time.Month;
import java.util.List;

import org.junit.Test;

import trader.common.exchangeable.Exchange;
import trader.common.exchangeable.ExchangeableData;
import trader.common.exchangeable.ExchangeableTradingTimes;
import trader.common.exchangeable.Future;
import trader.common.exchangeable.MarketDayUtil;
import trader.common.tick.PriceLevel;
import trader.common.util.PriceUtil;
import trader.common.util.TraderHomeUtil;
import trader.service.TraderHomeHelper;
import trader.service.md.MarketData;
import trader.service.md.MarketDataService;
import trader.service.ta.LeveledTimeSeries;
import trader.service.ta.LongNum;
import trader.service.ta.TimeSeriesLoader;
import trader.service.trade.TradeConstants.PosDirection;
import trader.service.util.SimpleBeansContainer;
import trader.simulator.SimMarketDataService;

@SuppressWarnings({"rawtypes","unchecked"})
public class MarketDataWaveBarTest {

    static {
        TraderHomeHelper.init(null);
    }

    static long tickStep;
    static int tickCount;

    @Test
    public void testSectionBarFromCtpTick_au1906() throws Exception
    {
        SimpleBeansContainer beansContainer = new SimpleBeansContainer();
        final SimMarketDataService mdService = new SimMarketDataService();
        mdService.init(beansContainer);
        beansContainer.addBean(MarketDataService.class, mdService);

        Future au1906 = Future.fromInstrument("au1906");
        ExchangeableData data = TraderHomeUtil.getExchangeableData();
        TimeSeriesLoader loader= new TimeSeriesLoader(beansContainer, data);
        loader
            .setInstrument(au1906)
            .setStartTradingDay(LocalDate.of(2018, 12, 13))
            .setEndTradingDay(LocalDate.of(2018, 12, 13))
            .setLevel(PriceLevel.TICKET);
        tickStep = PriceUtil.price2long(0.05);
        tickCount = 3;
        LocalDate date = LocalDate.of(2018, 12, 2);
        while(true) {
            if ( MarketDayUtil.isMarketDay(Exchange.SHFE, date)) {
                ExchangeableTradingTimes tradingTimes = au1906.exchange().getTradingTimes(au1906, date);
                loadTickData(tradingTimes, loader.loadMarketDataTicks(date, ExchangeableData.TICK_CTP));
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
        SimpleBeansContainer beansContainer = new SimpleBeansContainer();
        final SimMarketDataService mdService = new SimMarketDataService();
        mdService.init(beansContainer);
        beansContainer.addBean(MarketDataService.class, mdService);

        Future ru1901 = Future.fromInstrument("ru1901");
        ExchangeableData data = TraderHomeUtil.getExchangeableData();
        TimeSeriesLoader loader= new TimeSeriesLoader(beansContainer, data);
        loader
            .setInstrument(ru1901)
            .setStartTradingDay(LocalDate.of(2018, 12, 13))
            .setEndTradingDay(LocalDate.of(2018, 12, 13))
            .setLevel(PriceLevel.TICKET);
        tickStep = PriceUtil.price2long(5);
        tickCount = 3;
        LocalDate date = LocalDate.of(2018, 12, 2);
        while(true) {
            if ( MarketDayUtil.isMarketDay(Exchange.SHFE, date)) {
                ExchangeableTradingTimes tradingTimes = ru1901.exchange().getTradingTimes(ru1901, date);
                if ( data.exists(ru1901, ExchangeableData.TICK_CTP, date) ) {
                    loadTickData(tradingTimes, loader.loadMarketDataTicks(date, ExchangeableData.TICK_CTP));
                }
            }
            date = date.plusDays(1);
            if ( date.getMonth()!=Month.DECEMBER) {
                break;
            }
        }
    }

    private static void loadTickData(ExchangeableTradingTimes tradingTimes, List<MarketData> mds)
    {
        StackedTrendBarBuilder builder = new StackedTrendBarBuilder(tradingTimes);

        builder.getOption().strokeThreshold = (LongNum.fromRawValue(tickStep*tickCount));
        for(MarketData md:mds) {
            builder.update(md);
        }
        LeveledTimeSeries strokeSeries = builder.getTimeSeries(PriceLevel.STROKE);
        assertTrue(strokeSeries!=null);
        PosDirection lastStrokeDir = null;
        for(int i=0;i<strokeSeries.getBarCount();i++) {
            WaveBar strokeBar = (WaveBar)strokeSeries.getBar(i);
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
        LeveledTimeSeries sectionSeries = builder.getTimeSeries(PriceLevel.SECTION);
        assertTrue(sectionSeries!=null);
        WaveBar prevSectionBar = null;
        for(int i=0;i<sectionSeries.getBarCount();i++) {
            WaveBar sectionBar = (WaveBar)sectionSeries.getBar(i);
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
            long sectionSeconds = sectionBar.getTimePeriod().getSeconds();
            long strokeSeconds = 0;
            for(WaveBar bar:(List<WaveBar>)sectionBar.getBars()) {
                strokeSeconds+= bar.getTimePeriod().getSeconds();
            }
            assertTrue( Math.abs(sectionSeconds-strokeSeconds)<=(sectionBar.getBars().size()) );
        }

        System.out.println("---- SECTION DUMP ----");
        for(int i=0;i<sectionSeries.getBarCount();i++) {
            WaveBar sectionBar = (WaveBar)sectionSeries.getBar(i);
            System.out.println(sectionBar);
            for(Object strokeBar:sectionBar.getBars()) {
                System.out.println("\t"+strokeBar);
            }
        }
    }
}
