package trader.service.md.ctp;

import java.util.Map;

import trader.common.beans.Discoverable;
import trader.common.util.CSVMarshallHelper;
import trader.common.util.csv.CtpCSVMarshallHelper;
import trader.service.md.MarketDataProducer;
import trader.service.md.MarketDataProducerFactory;
import trader.service.md.MarketDataService;
import trader.service.md.MarketDataServiceImpl;

@Discoverable(interfaceClass = MarketDataProducerFactory.class, purpose = MarketDataProducer.PROVIDER_CTP)
public class CtpMarketDataProducerFactory implements MarketDataProducerFactory {

    @Override
    public MarketDataProducer create(MarketDataService mdService, Map configMap) {
        return new CtpMarketDataProducer((MarketDataServiceImpl)mdService, configMap);
    }

    @Override
    public CSVMarshallHelper createCSVMarshallHelper() {
        return new CtpCSVMarshallHelper();
    }

}
