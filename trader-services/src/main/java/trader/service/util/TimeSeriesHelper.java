package trader.service.util;

import trader.common.exchangeable.ExchangeableData;
import trader.common.util.TraderHomeUtil;
import trader.service.md.MarketDataService;
import trader.service.ta.BarSeriesLoader;
import trader.simulator.SimMarketDataService;

public class TimeSeriesHelper {

    public static BarSeriesLoader getTimeSeriesLoader() throws Exception
    {
        SimpleBeansContainer beansContainer = new SimpleBeansContainer();
        final SimMarketDataService mdService = new SimMarketDataService();
        mdService.init(beansContainer);
        beansContainer.addBean(MarketDataService.class, mdService);

        ExchangeableData data = TraderHomeUtil.getExchangeableData();
        BarSeriesLoader loader= new BarSeriesLoader(beansContainer, data);

        return loader;
    }

}
