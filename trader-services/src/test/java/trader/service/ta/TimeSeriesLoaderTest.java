package trader.service.ta;

import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.TimeSeries;

import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableData;
import trader.common.tick.PriceLevel;
import trader.common.util.TraderHomeUtil;
import trader.service.TraderHomeTestUtil;
import trader.service.md.MarketDataService;
import trader.simulator.SimBeansContainer;
import trader.simulator.SimMarketDataService;

public class TimeSeriesLoaderTest {

    @Before
    public void setup() {
        TraderHomeTestUtil.initRepoistoryDir();
    }

    @Test
    public void testBarBeginEndTime() {
        Exchangeable ru1901 = Exchangeable.fromString("ru1901");
        {
            LocalDateTime time = LocalDateTime.of(2018, Month.OCTOBER, 11, 10, 14);
            assertTrue( TimeSeriesLoader.getBarBeginTime(ru1901, PriceLevel.MIN5, -1, time).getMinute()==10 );
            assertTrue( TimeSeriesLoader.getBarEndTime(ru1901, PriceLevel.MIN5, -1, time).getMinute()==15 );
        }
        {
            LocalDateTime time = LocalDateTime.of(2018, Month.OCTOBER, 11, 10, 15);
            assertTrue( TimeSeriesLoader.getBarBeginTime(ru1901, PriceLevel.MIN5, -1, time).getMinute()==10 );
            assertTrue( TimeSeriesLoader.getBarEndTime(ru1901, PriceLevel.MIN5, -1, time).getMinute()==15 );
        }
        {
            LocalDateTime time = LocalDateTime.of(2018, Month.OCTOBER, 11, 10, 31);
            assertTrue( TimeSeriesLoader.getBarBeginTime(ru1901, PriceLevel.MIN5, -1, time).getMinute()==30 );
            assertTrue( TimeSeriesLoader.getBarEndTime(ru1901, PriceLevel.MIN5, -1, time).getMinute()==35 );
        }
        {
            LocalDateTime time = LocalDateTime.of(2018, Month.OCTOBER, 11, 13, 31);
            assertTrue( TimeSeriesLoader.getBarBeginTime(ru1901, PriceLevel.MIN15, -1, time).getMinute()==30 );
            assertTrue( TimeSeriesLoader.getBarEndTime(ru1901, PriceLevel.MIN15, -1, time).getMinute()==45 );
        }
    }

    @Test
    public void testCtpTick() throws Exception
    {
        SimBeansContainer beansContainer = new SimBeansContainer();
        final SimMarketDataService mdService = new SimMarketDataService();
        mdService.init(beansContainer);
        beansContainer.addBean(MarketDataService.class, mdService);

        ExchangeableData data = TraderHomeUtil.getExchangeableData();
        TimeSeriesLoader loader= new TimeSeriesLoader(beansContainer, data);
        loader
            .setExchangeable(Exchangeable.fromString("ru1901"))
            .setStartTradingDay(LocalDate.of(2018, 12, 03))
            .setEndTradingDay(LocalDate.of(2018, 12, 03))
            .setLevel(PriceLevel.TICKET);

        TimeSeries min1Series = loader.load();
        assertTrue(min1Series.getBarCount()>0);

    }

    @Test
    public void testMinFromCtpTick() throws Exception
    {
        SimBeansContainer beansContainer = new SimBeansContainer();
        final SimMarketDataService mdService = new SimMarketDataService();
        mdService.init(beansContainer);
        beansContainer.addBean(MarketDataService.class, mdService);

        ExchangeableData data = TraderHomeUtil.getExchangeableData();
        TimeSeriesLoader loader= new TimeSeriesLoader(beansContainer, data);
        loader
            .setExchangeable(Exchangeable.fromString("ru1901"))
            .setStartTradingDay(LocalDate.of(2018, 12, 3))
            .setEndTradingDay(LocalDate.of(2018, 12, 03))
            .setLevel(PriceLevel.MIN1);

        TimeSeries min1Series = loader.load();
        assertTrue(min1Series.getBarCount()>0);

        loader.setLevel(PriceLevel.MIN3);
        TimeSeries min3Series = loader.load();
        assertTrue(min3Series.getBarCount()>0);

        assertTrue((min1Series.getBarCount()+2)/3==min3Series.getBarCount());

        loader.setLevel(PriceLevel.MIN5);
        TimeSeries min5Series = loader.load();
        assertTrue(min5Series.getBarCount()>0);

        assertTrue((min1Series.getBarCount())/5==min5Series.getBarCount());
    }


    @Test
    public void testMinFromCtpTick_au1906() throws Exception
    {
        SimBeansContainer beansContainer = new SimBeansContainer();
        final SimMarketDataService mdService = new SimMarketDataService();
        mdService.init(beansContainer);
        beansContainer.addBean(MarketDataService.class, mdService);

        ExchangeableData data = TraderHomeUtil.getExchangeableData();
        TimeSeriesLoader loader= new TimeSeriesLoader(beansContainer, data);
        loader
            .setExchangeable(Exchangeable.fromString("au1906"))
            .setStartTradingDay(LocalDate.of(2018, 12, 13))
            .setEndTradingDay(LocalDate.of(2018, 12, 13))
            .setLevel(PriceLevel.MIN1);

        TimeSeries min1Series = loader.load();
        assertTrue(min1Series.getBarCount()>0);

        loader.setLevel(PriceLevel.MIN3);
        TimeSeries min3Series = loader.load();
        assertTrue(min3Series.getBarCount()>0);

        assertTrue((min1Series.getBarCount()+2)/3==min3Series.getBarCount());

        loader.setLevel(PriceLevel.MIN5);
        TimeSeries min5Series = loader.load();
        assertTrue(min5Series.getBarCount()>0);

        assertTrue((min1Series.getBarCount())/5==min5Series.getBarCount());
    }

    @Test
    public void testMinFromMin1() throws Exception
    {
        SimBeansContainer beansContainer = new SimBeansContainer();
        final SimMarketDataService mdService = new SimMarketDataService();
        mdService.init(beansContainer);
        beansContainer.addBean(MarketDataService.class, mdService);

        ExchangeableData data = TraderHomeUtil.getExchangeableData();
        TimeSeriesLoader loader= new TimeSeriesLoader(beansContainer, data);
        loader
            .setExchangeable(Exchangeable.fromString("ru1901"))
            .setStartTradingDay(LocalDate.of(2018, 12, 03))
            .setEndTradingDay(LocalDate.of(2018, 12, 03))
            .setLevel(PriceLevel.MIN1);

        TimeSeries min1Series = loader.load();
        assertTrue(min1Series.getBarCount()>0);

        loader.setLevel(PriceLevel.MIN3);
        TimeSeries min3Series = loader.load();
        assertTrue(min3Series.getBarCount()>0);

        assertTrue((min1Series.getBarCount()+2)/3==min3Series.getBarCount());

        loader.setLevel(PriceLevel.MIN5);
        TimeSeries min5Series = loader.load();
        assertTrue(min5Series.getBarCount()>0);

        assertTrue((min1Series.getBarCount())/5==min5Series.getBarCount());
    }

}
