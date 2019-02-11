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
import trader.service.log.LogServiceImpl;
import trader.service.md.MarketDataService;
import trader.service.ta.TAServiceImpl;
import trader.service.trade.Account;
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
        LogServiceImpl.setLogLevel("trader.service", "INFO");

        LocalDateTime[][] timeRanges = new LocalDateTime[][] {
            { LocalDateTime.of(2018, Month.DECEMBER, 27, 20, 55),
              LocalDateTime.of(2018, Month.DECEMBER, 28, 2, 35)
            },{ LocalDateTime.of(2018, Month.DECEMBER, 28, 8, 55),
              LocalDateTime.of(2018, Month.DECEMBER, 28, 15, 05)
            }

        };
        Exchangeable au1906 = Exchangeable.fromString("au1906");
        BeansContainer beansContainer = initBeans(au1906, timeRanges );
        SimMarketTimeService mtService = beansContainer.getBean(SimMarketTimeService.class);
        //时间片段循环
        while(mtService.nextTimePiece());

        TradeService tradeService = beansContainer.getBean(TradeService.class);
        Account account = tradeService.getPrimaryAccount();
        System.out.println(account);

    }

    private static BeansContainer initBeans(Exchangeable e, LocalDateTime[][] timeRanges) throws Exception
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

        LocalDate tradingDay = MarketDayUtil.getTradingDay(Exchange.SHFE, timeRanges[0][0]);
        assertTrue(tradingDay!=null);
        scheduledExecutorService.init(beansContainer);
        mtService.setTimeRanges(tradingDay, timeRanges );
        mdService.init(beansContainer);
        taService.init(beansContainer);
        tradeService.init(beansContainer);
        tradletService.init(beansContainer);
        return beansContainer;
    }

}
