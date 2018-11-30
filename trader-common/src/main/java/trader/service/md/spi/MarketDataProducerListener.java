package trader.service.md.spi;

import trader.service.ServiceConstants.ConnState;
import trader.service.md.MarketData;

public interface MarketDataProducerListener {

    public void onStateChanged(AbsMarketDataProducer producer, ConnState lastStatus);

    public void onMarketData(MarketData md);
}
