package trader.service;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import trader.common.beans.BeansContainer;
import trader.service.event.AsyncEventServiceImpl;
import trader.service.md.MarketDataService;
import trader.service.ta.TechnicalAnalysisService;
import trader.service.trade.TradeService;
import trader.service.tradlet.TradletService;

/**
 * 交易的服务实现类的组装代码
 */
@Service
public class ServiceAssembler {

    @Autowired
    private BeansContainer beansContainer;

    @Autowired
    AsyncEventServiceImpl asyncEventService;

    @Autowired
    private MarketDataService marketDataService;

    @Autowired
    private TradeService tradeService;

    @Autowired
    private TechnicalAnalysisService taService;

    @Autowired
    private TradletService tradletService;

    @PostConstruct
    public void init() throws Exception
    {
        asyncEventService.init(beansContainer);
        marketDataService.init(beansContainer);
        taService.init(beansContainer);
        tradeService.init(beansContainer);
        tradletService.init(beansContainer);
        asyncEventService.start();
    }

}
