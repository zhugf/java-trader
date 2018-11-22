package trader.service.md;

import java.util.Map;

import trader.common.util.CSVMarshallHelper;

/**
 * 扩展实现类工厂
 */
public interface MarketDataProducerFactory {

    public MarketDataProducer create(MarketDataService mdService, Map configMap);

    public CSVMarshallHelper createCSVMarshallHelper();
}
