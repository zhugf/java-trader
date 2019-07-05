package trader.service.md.web;

import java.util.Map;

import trader.common.beans.BeansContainer;
import trader.common.beans.Discoverable;
import trader.common.util.CSVMarshallHelper;
import trader.common.util.csv.CtpCSVMarshallHelper;
import trader.service.md.MarketDataProducer;
import trader.service.md.MarketDataProducerFactory;

@Discoverable(interfaceClass = MarketDataProducerFactory.class, purpose = MarketDataProducer.PROVIDER_WEB)
@SuppressWarnings("rawtypes")
public class WebMarketDataProducerFactory implements MarketDataProducerFactory {

    @Override
    public MarketDataProducer create(BeansContainer beansContainer, Map configMap) {
        return new WebMarketDataProducer(beansContainer, configMap);
    }

    @Override
    public CSVMarshallHelper createCSVMarshallHelper() {
        return new CtpCSVMarshallHelper();
    }

}
