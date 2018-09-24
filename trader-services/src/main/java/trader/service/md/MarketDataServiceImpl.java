package trader.service.md;

import java.util.Collection;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import trader.common.exchangeable.Exchangeable;

/**
 * 行情数据的接收和聚合
 */
@Service
public class MarketDataServiceImpl implements MarketDataService {
    private final static Logger logger = LoggerFactory.getLogger(MarketDataServiceImpl.class);


    @PostConstruct
    public void init() {

    }

    @PreDestroy
    public void destroy() {

    }


    @Override
    public Collection<MarketDataProducer> getProducers() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void addMarketDataListener(MarketDataListener listener, Exchangeable... instrumentIds) {
        // TODO Auto-generated method stub

    }

}
