package trader.service.ta;

import static org.junit.Assert.assertTrue;

import java.time.LocalDate;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.TimeSeries;

import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableData;
import trader.common.tick.PriceLevel;
import trader.common.util.TraderHomeUtil;
import trader.service.TraderHomeTestUtil;

public class TimeSeriesLoaderTest {

    @Before
    public void setup() {
        TraderHomeTestUtil.initRepoistoryDir();
    }

    @Test
    public void testMinFromCtpTick() throws Exception
    {
        ExchangeableData data = TraderHomeUtil.getExchangeableData();
        TimeSeriesLoader loader= new TimeSeriesLoader(data);
        loader
            .setExchangeable(Exchangeable.fromString("ru1901"))
            .setStartTradingDay(LocalDate.of(2018, 10, 10))
            .setEndTradingDay(LocalDate.of(2018, 10, 10))
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
        ExchangeableData data = TraderHomeUtil.getExchangeableData();
        TimeSeriesLoader loader= new TimeSeriesLoader(data);
        loader
            .setExchangeable(Exchangeable.fromString("ru1901"))
            .setStartTradingDay(LocalDate.of(2018, 10, 11))
            .setEndTradingDay(LocalDate.of(2018, 10, 11))
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
