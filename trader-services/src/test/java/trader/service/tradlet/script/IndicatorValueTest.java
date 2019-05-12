package trader.service.tradlet.script;

import static org.junit.Assert.assertTrue;

import java.time.LocalDate;

import org.junit.Test;
import org.ta4j.core.TimeSeries;

import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableData;
import trader.common.tick.PriceLevel;
import trader.common.util.TraderHomeUtil;
import trader.service.TraderHomeHelper;
import trader.service.md.MarketDataService;
import trader.service.ta.Bar2;
import trader.service.ta.TimeSeriesLoader;
import trader.simulator.SimBeansContainer;
import trader.simulator.SimMarketDataService;

public class IndicatorValueTest {

    static {
        TraderHomeHelper.init(null);
    }

    @Test
    public void test() throws Exception
    {
        Exchangeable e = Exchangeable.fromString("ru1901");
        SimBeansContainer beansContainer = new SimBeansContainer();
        final SimMarketDataService mdService = new SimMarketDataService();
        mdService.init(beansContainer);
        beansContainer.addBean(MarketDataService.class, mdService);

        ExchangeableData data = TraderHomeUtil.getExchangeableData();
        TimeSeriesLoader loader= new TimeSeriesLoader(beansContainer, data);
        loader
            .setExchangeable(e)
            .setStartTradingDay(LocalDate.of(2018, 12, 3))
            .setEndTradingDay(LocalDate.of(2018, 12, 03))
            .setLevel(PriceLevel.MIN1);

        TimeSeries min1Series = loader.load();
        SimpleIndicator indicator = SimpleIndicator.createFromSeries(min1Series, (Bar2 bar)->{
            return bar.getClosePrice();
        });

        assertTrue(indicator.getTimeSeries()!=null);
    }

}
