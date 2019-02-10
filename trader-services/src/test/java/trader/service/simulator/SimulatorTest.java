package trader.service.simulator;

import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.concurrent.ScheduledExecutorService;

import org.junit.Test;

import trader.common.beans.BeansContainer;
import trader.common.exchangeable.Exchange;
import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.MarketDayUtil;
import trader.service.TraderHomeHelper;
import trader.service.data.KVStoreService;
import trader.service.md.MarketDataService;
import trader.service.ta.TAServiceImpl;
import trader.service.trade.MarketTimeService;
import trader.service.trade.TradeService;
import trader.service.tradlet.TradletService;
import trader.simulator.SimBeansContainer;
import trader.simulator.SimKVStoreService;
import trader.simulator.SimMarketDataService;
import trader.simulator.SimMarketTimeService;
import trader.simulator.SimScheduledExecutorService;
import trader.simulator.SimTradletService;
import trader.simulator.trade.SimTradeService;

/**
 * 回测功能的自测
 */
public class SimulatorTest {
    static {
        TraderHomeHelper.init();
    }

    @Test
    public void test_au1906() throws Exception
    {
        LocalDateTime beginTime = LocalDateTime.of(2018, Month.DECEMBER, 28, 8, 50);
        LocalDateTime endTime = LocalDateTime.of(2018, Month.DECEMBER, 28, 15, 04);
        Exchangeable au1906 = Exchangeable.fromString("au1906");
        BeansContainer beansContainer = initBeans(beginTime, endTime, au1906);
    }

    private static BeansContainer initBeans(LocalDateTime beginTime, LocalDateTime endTime, Exchangeable e) throws Exception
    {
        SimBeansContainer beansContainer = new SimBeansContainer();
        SimMarketTimeService mtService = new SimMarketTimeService();
        SimScheduledExecutorService scheduledExecutorService = new SimScheduledExecutorService();
        SimMarketDataService mdService = new SimMarketDataService();
        SimKVStoreService kvStoreService = new SimKVStoreService();
        SimTradeService tradeService = new SimTradeService();
        TAServiceImpl taService = new TAServiceImpl();
        SimTradletService tradletService = new SimTradletService();

        beansContainer.addBean(MarketTimeService.class, mtService);
        beansContainer.addBean(ScheduledExecutorService.class, scheduledExecutorService);
        beansContainer.addBean(MarketDataService.class, mdService);
        beansContainer.addBean(KVStoreService.class, kvStoreService);
        beansContainer.addBean(TradeService.class, tradeService);
        beansContainer.addBean(TAServiceImpl.class, taService);
        beansContainer.addBean(TradletService.class, tradletService);

        LocalDate tradingDay = MarketDayUtil.getTradingDay(Exchange.SHFE, beginTime);
        assertTrue(tradingDay!=null);
        scheduledExecutorService.init(beansContainer);
        mtService.setTimeRange(tradingDay, beginTime, endTime);
        mdService.init(beansContainer);
        taService.init(beansContainer);
        tradeService.init(beansContainer);
        //TODO 使用实际的配置文件加载
        tradletService.init(beansContainer);
        return beansContainer;
    }

}
