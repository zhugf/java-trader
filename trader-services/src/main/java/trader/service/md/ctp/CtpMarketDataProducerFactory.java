package trader.service.md.ctp;

import java.util.Map;

import trader.common.beans.Discoverable;
import trader.common.util.CSVMarshallHelper;
import trader.common.util.csv.CtpCSVMarshallHelper;
import trader.service.md.MarketDataProducer;
import trader.service.md.MarketDataProducerFactory;

@Discoverable(interfaceClass = MarketDataProducerFactory.class, purpose = MarketDataProducer.PROVIDER_CTP)
public class CtpMarketDataProducerFactory implements MarketDataProducerFactory {

    @Override
    public MarketDataProducer create(Map configMap) {
        return new CtpMarketDataProducer(configMap);
    }

    @Override
    public CSVMarshallHelper createCSVMarshallHelper() {
        return new CtpCSVMarshallHelper();
    }

}
