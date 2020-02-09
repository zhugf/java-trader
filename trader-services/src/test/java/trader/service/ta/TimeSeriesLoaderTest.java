package trader.service.ta;

import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;

import org.junit.Test;
import org.ta4j.core.BarSeries;

import trader.common.exchangeable.Exchange;
import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableData;
import trader.common.exchangeable.ExchangeableTradingTimes;
import trader.common.tick.PriceLevel;
import trader.common.util.TraderHomeUtil;
import trader.service.TraderHomeHelper;
import trader.service.md.MarketDataService;
import trader.service.util.SimpleBeansContainer;
import trader.simulator.SimMarketDataService;

public class TimeSeriesLoaderTest {

    static {
        TraderHomeHelper.init(null);
    }

    @Test
    public void testBarBeginEndTime() {
        Exchangeable ru1901 = Exchangeable.fromString("ru1901");
        {
            LocalDateTime time = LocalDateTime.of(2018, Month.OCTOBER, 11, 10, 24);
            LocalDateTime time2 = LocalDateTime.of(2018, Month.OCTOBER, 11, 10, 27);

            ExchangeableTradingTimes tradingTimes = ru1901.exchange().detectTradingTimes(ru1901, time);
            int barIndex = BarSeriesLoader.getBarIndex(tradingTimes, PriceLevel.MIN5, time);
            int barIndex2 = BarSeriesLoader.getBarIndex(tradingTimes, PriceLevel.MIN5, time2);
            assertTrue(barIndex==barIndex2);
        }
        {
            LocalDateTime time = LocalDateTime.of(2018, Month.OCTOBER, 11, 10, 14);
            ExchangeableTradingTimes tradingTimes = ru1901.exchange().detectTradingTimes(ru1901, time);
            LocalDateTime[] barTimes = BarSeriesLoader.getBarTimes(tradingTimes, PriceLevel.MIN5, -1, time);
            assertTrue( barTimes[0].getMinute()==10 );
            assertTrue( barTimes[1].getMinute()==15 );
        }
        {
            LocalDateTime time = LocalDateTime.of(2018, Month.OCTOBER, 11, 10, 15);
            ExchangeableTradingTimes tradingTimes = ru1901.exchange().detectTradingTimes(ru1901, time);
            LocalDateTime[] barTimes = BarSeriesLoader.getBarTimes(tradingTimes, PriceLevel.MIN5, -1, time);
            assertTrue( barTimes[0].getMinute()==10 );
            assertTrue( barTimes[1].getMinute()==15 );
        }
        {
            LocalDateTime time = LocalDateTime.of(2018, Month.OCTOBER, 11, 10, 31);
            ExchangeableTradingTimes tradingTimes = ru1901.exchange().detectTradingTimes(ru1901, time);

            LocalDateTime[] barTimes = BarSeriesLoader.getBarTimes(tradingTimes, PriceLevel.MIN5, -1, time);
            assertTrue( barTimes[0].getMinute()==30 );
            assertTrue( barTimes[1].getMinute()==35 );
        }
        {
            LocalDateTime time = LocalDateTime.of(2018, Month.OCTOBER, 11, 13, 31);
            ExchangeableTradingTimes tradingTimes = ru1901.exchange().detectTradingTimes(ru1901, time);
            LocalDateTime[] barTimes = BarSeriesLoader.getBarTimes(tradingTimes, PriceLevel.MIN15, -1, time);
            assertTrue( barTimes[0].getMinute()==30 );
            assertTrue( barTimes[1].getMinute()==45 );
        }
    }

    @Test
    public void test_dce_instruments() {
        //j1909--焦炭
        {
            Exchangeable j1909 = Exchangeable.fromString("j1909");
            assertTrue(j1909.exchange()==Exchange.DCE);

            LocalDateTime time = LocalDateTime.of(2019, Month.MARCH, 29, 23, 0);
            LocalDateTime time2 = LocalDateTime.of(2019, Month.MARCH, 29, 22, 59);
            ExchangeableTradingTimes tradingTimes = j1909.exchange().detectTradingTimes(j1909, time);

            int barIndex = BarSeriesLoader.getBarIndex(tradingTimes, PriceLevel.MIN5, time);
            int barIndex2 = BarSeriesLoader.getBarIndex(tradingTimes, PriceLevel.MIN5, time2);
            assertTrue(barIndex==barIndex2);
        }
    }

    @Test
    public void testBarIndex_au1906() {
        Exchangeable au1906 = Exchangeable.fromString("au1906");
        LocalDateTime time = LocalDateTime.of(2018, Month.DECEMBER, 13, 02, 29);
        ExchangeableTradingTimes tradingTimes = au1906.exchange().detectTradingTimes(au1906, time);

        LocalDateTime time2 = LocalDateTime.of(2018, Month.DECEMBER, 13, 02, 30);
        int barIndex2 = BarSeriesLoader.getBarIndex(tradingTimes, PriceLevel.MIN1, time2);
        int barIndex = BarSeriesLoader.getBarIndex(tradingTimes, PriceLevel.MIN1, time);

        LocalDateTime time3 = LocalDateTime.of(2018, Month.DECEMBER, 13, 02, 30, 00).withNano(500*1000000);
        int barIndex3 = BarSeriesLoader.getBarIndex(tradingTimes, PriceLevel.MIN1, time3);
        assertTrue(barIndex==barIndex3);

        assertTrue(barIndex==barIndex2);
    }

    @Test
    public void testCtpTick() throws Exception
    {
        SimpleBeansContainer beansContainer = new SimpleBeansContainer();
        final SimMarketDataService mdService = new SimMarketDataService();
        mdService.init(beansContainer);
        beansContainer.addBean(MarketDataService.class, mdService);

        ExchangeableData data = TraderHomeUtil.getExchangeableData();
        BarSeriesLoader loader= new BarSeriesLoader(beansContainer, data);

        LocalDate beginTradingDay = LocalDate.of(2018, 12, 03);
        LocalDate endTradingDay = LocalDate.of(2018, 12, 04);
        loader
            .setInstrument(Exchangeable.fromString("ru1901"))
            .setStartTradingDay(beginTradingDay)
            .setEndTradingDay(endTradingDay)
            .setLevel(PriceLevel.MIN1);

        LeveledBarSeries series = loader.load();
        assertTrue(series.getBarCount()>0);

        LeveledBarSeries series0 = LeveledBarSeries.getDailySeries(endTradingDay, series, true);
        assertTrue(series0.getBarCount()<series.getBarCount() && series0.getBarCount()>0);
        assertTrue(series0.getBar(series0.getEndIndex())==series.getBar(series.getEndIndex()));

        LeveledBarSeries series2 = LeveledBarSeries.getDailySeries(endTradingDay, series, false);
        assertTrue(series2.getBarCount()==series0.getBarCount()-1);
        assertTrue(series2.getBar(series2.getEndIndex())==series0.getBar(series0.getEndIndex()-1));

        LeveledBarSeries series3 = (LeveledBarSeries)series0.getSubSeries(0, 1);
        assertTrue(series3.getBarCount()==1);

        LeveledBarSeries series4 = LeveledBarSeries.getDailySeries(endTradingDay, series3, false);
        assertTrue(series4.getBarCount()==0);
    }

    @Test
    public void testMinFromCtpTick() throws Exception
    {
        SimpleBeansContainer beansContainer = new SimpleBeansContainer();
        final SimMarketDataService mdService = new SimMarketDataService();
        mdService.init(beansContainer);
        beansContainer.addBean(MarketDataService.class, mdService);

        ExchangeableData data = TraderHomeUtil.getExchangeableData();
        BarSeriesLoader loader= new BarSeriesLoader(beansContainer, data);
        loader
            .setInstrument(Exchangeable.fromString("ru1901"))
            .setStartTradingDay(LocalDate.of(2018, 12, 3))
            .setEndTradingDay(LocalDate.of(2018, 12, 03))
            .setLevel(PriceLevel.MIN1);

        BarSeries min1Series = loader.load();
        assertTrue(min1Series.getBarCount()>0);

        loader.setLevel(PriceLevel.MIN3);
        BarSeries min3Series = loader.load();
        assertTrue(min3Series.getBarCount()>0);

        assertTrue((min1Series.getBarCount()+2)/3==min3Series.getBarCount());

        loader.setLevel(PriceLevel.MIN5);
        BarSeries min5Series = loader.load();
        assertTrue(min5Series.getBarCount()>0);

        assertTrue((min1Series.getBarCount())/5==min5Series.getBarCount());
    }


    @Test
    public void testVolFromCtpTick() throws Exception
    {
        SimpleBeansContainer beansContainer = new SimpleBeansContainer();
        final SimMarketDataService mdService = new SimMarketDataService();
        mdService.init(beansContainer);
        beansContainer.addBean(MarketDataService.class, mdService);

        ExchangeableData data = TraderHomeUtil.getExchangeableData();
        BarSeriesLoader loader= new BarSeriesLoader(beansContainer, data);
        loader
            .setInstrument(Exchangeable.fromString("ru1901"))
            .setStartTradingDay(LocalDate.of(2018, 12, 3))
            .setEndTradingDay(LocalDate.of(2018, 12, 03))
            .setLevel(PriceLevel.VOL1K);

        BarSeries min1Series = loader.load();
        assertTrue(min1Series.getBarCount()>0);
    }

    @Test
    public void testMinFromCtpTick_au1906() throws Exception
    {
        SimpleBeansContainer beansContainer = new SimpleBeansContainer();
        final SimMarketDataService mdService = new SimMarketDataService();
        mdService.init(beansContainer);
        beansContainer.addBean(MarketDataService.class, mdService);

        ExchangeableData data = TraderHomeUtil.getExchangeableData();
        BarSeriesLoader loader= new BarSeriesLoader(beansContainer, data);
        loader
            .setInstrument(Exchangeable.fromString("au1906"))
            .setStartTradingDay(LocalDate.of(2018, 12, 13))
            .setEndTradingDay(LocalDate.of(2018, 12, 13))
            .setLevel(PriceLevel.MIN1);

        BarSeries min1Series = loader.load();
        assertTrue(min1Series.getBarCount()>0);

        loader.setLevel(PriceLevel.MIN3);
        BarSeries min3Series = loader.load();
        assertTrue(min3Series.getBarCount()>0);

        assertTrue((min1Series.getBarCount()+2)/3==min3Series.getBarCount());

        loader.setLevel(PriceLevel.MIN5);
        BarSeries min5Series = loader.load();
        assertTrue(min5Series.getBarCount()>0);

        assertTrue((min1Series.getBarCount())/5==min5Series.getBarCount());
    }

    @Test
    public void testMinFromMin1() throws Exception
    {
        SimpleBeansContainer beansContainer = new SimpleBeansContainer();
        final SimMarketDataService mdService = new SimMarketDataService();
        mdService.init(beansContainer);
        beansContainer.addBean(MarketDataService.class, mdService);

        ExchangeableData data = TraderHomeUtil.getExchangeableData();
        BarSeriesLoader loader= new BarSeriesLoader(beansContainer, data);
        loader
            .setInstrument(Exchangeable.fromString("ru1901"))
            .setStartTradingDay(LocalDate.of(2018, 12, 03))
            .setEndTradingDay(LocalDate.of(2018, 12, 03))
            .setLevel(PriceLevel.MIN1);

        BarSeries min1Series = loader.load();
        assertTrue(min1Series.getBarCount()>0);

        loader.setLevel(PriceLevel.MIN3);
        BarSeries min3Series = loader.load();
        assertTrue(min3Series.getBarCount()>0);

        assertTrue((min1Series.getBarCount()+2)/3==min3Series.getBarCount());

        loader.setLevel(PriceLevel.MIN5);
        BarSeries min5Series = loader.load();
        assertTrue(min5Series.getBarCount()>0);

        assertTrue((min1Series.getBarCount())/5==min5Series.getBarCount());
    }

    @Test
    public void testDaySeries() throws Exception
    {

        SimpleBeansContainer beansContainer = new SimpleBeansContainer();
        final SimMarketDataService mdService = new SimMarketDataService();
        mdService.init(beansContainer);
        beansContainer.addBean(MarketDataService.class, mdService);

        ExchangeableData data = TraderHomeUtil.getExchangeableData();
        BarSeriesLoader loader= new BarSeriesLoader(beansContainer, data);
        loader
            .setInstrument(Exchangeable.fromString("ru1901"))
            .setStartTradingDay(LocalDate.of(2018, 1, 03))
            .setEndTradingDay(LocalDate.of(2018, 12, 28))
            .setLevel(PriceLevel.DAY);

        BarSeries daySeries = loader.load();
        assertTrue(daySeries.getBarCount()>0);
    }

}
