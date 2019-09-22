package trader.service.util;

import trader.common.exchangeable.ExchangeableData;
import trader.common.util.TraderHomeUtil;
import trader.service.md.MarketDataService;
import trader.service.ta.TimeSeriesLoader;
import trader.simulator.SimMarketDataService;

public class TimeSeriesHelper {

    public static TimeSeriesLoader getTimeSeriesLoader() throws Exception
    {
        SimpleBeansContainer beansContainer = new SimpleBeansContainer();
        final SimMarketDataService mdService = new SimMarketDataService();
        mdService.init(beansContainer);
        beansContainer.addBean(MarketDataService.class, mdService);

        ExchangeableData data = TraderHomeUtil.getExchangeableData();
        TimeSeriesLoader loader= new TimeSeriesLoader(beansContainer, data);

        return loader;
    }

}
