package trader.service.ta;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.time.LocalDate;

import org.junit.Test;
import org.ta4j.core.TimeSeries;

import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableData;
import trader.common.tick.PriceLevel;

public class TimeSeriesLoaderTest {

    private static final ExchangeableData data =
        new ExchangeableData( new File( TimeSeriesLoaderTest.class.getClassLoader().getResource("data/shfe/ru1901/2018.tick-ctp.zip").getFile())
        .getParentFile().getParentFile().getParentFile() );

    @Test
    public void testMinFromCtpTick() throws Exception
    {
        TimeSeriesLoader loader= new TimeSeriesLoader(data);
        loader
            .setExchangeable(Exchangeable.fromString("ru1901"))
            .setBeginDate(LocalDate.of(2018, 10, 10))
            .setEndDate(LocalDate.of(2018, 10, 10))
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
        TimeSeriesLoader loader= new TimeSeriesLoader(data);
        loader
            .setExchangeable(Exchangeable.fromString("ru1901"))
            .setBeginDate(LocalDate.of(2018, 10, 11))
            .setEndDate(LocalDate.of(2018, 10, 11))
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
