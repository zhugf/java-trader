package trader.service;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import trader.service.md.MarketDataService;
import trader.service.trade.TradeService;

/**
 * 交易的服务实现类的组装代码
 */
@Service
public class ServiceAssembler {

    @Autowired
    private MarketDataService marketDataService;

    @Autowired
    private TradeService tradeService;

    @PostConstruct
    public void init() {

    }

}
