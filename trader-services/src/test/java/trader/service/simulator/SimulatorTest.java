package trader.service.simulator;

import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.time.Month;
import java.util.concurrent.ScheduledExecutorService;

import org.junit.Test;

import trader.common.beans.BeansContainer;
import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableTradingTimes;
import trader.service.TraderHomeHelper;
import trader.service.data.KVStoreService;
import trader.service.log.LogServiceImpl;
import trader.service.md.MarketDataService;
import trader.service.ta.TAServiceImpl;
import trader.service.trade.Account;
import trader.service.trade.MarketTimeService;
import trader.service.trade.TradeService;
import trader.service.tradlet.TradletService;
import trader.service.util.SimpleBeansContainer;
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
        LogServiceImpl.setLogLevel("org.reflections", "ERROR");
        LogServiceImpl.setLogLevel("org.apache.commons", "INFO");
        LogServiceImpl.setLogLevel("trader", "INFO");
        TraderHomeHelper.init(null);
    }

    @Test
    public void test_au1906() throws Exception
    {
        LogServiceImpl.setLogLevel("trader.service", "INFO");

        Exchangeable au1906 = Exchangeable.fromString("au1906");
        LocalDate tradingDay = LocalDate.of(2018, Month.DECEMBER, 28);
        BeansContainer beansContainer = initBeans(au1906, tradingDay );
        SimMarketTimeService mtService = beansContainer.getBean(SimMarketTimeService.class);
        //时间片段循环
        while(mtService.nextTimePiece());

        TradeService tradeService = beansContainer.getBean(TradeService.class);
        Account account = tradeService.getPrimaryAccount();
        System.out.println(account);

    }

    private static BeansContainer initBeans(Exchangeable e, LocalDate tradingDay) throws Exception
    {
        SimpleBeansContainer beansContainer = new SimpleBeansContainer();
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

        assertTrue(tradingDay!=null);
        scheduledExecutorService.init(beansContainer);
        ExchangeableTradingTimes tradingTimes = e.exchange().getTradingTimes(e, tradingDay);
        mtService.setTimeRanges(tradingDay, tradingTimes.getMarketTimes() );
        mdService.init(beansContainer);
        taService.init(beansContainer);
        tradeService.init(beansContainer);
        tradletService.init(beansContainer);
        return beansContainer;
    }

}
