package trader.service.tradlet.script;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.time.LocalDate;
import java.time.Month;
import java.util.concurrent.ScheduledExecutorService;

import org.junit.Test;

import trader.common.beans.BeansContainer;
import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableTradingTimes;
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
 * 测试GROOVY脚本能力
 */
public class GroovyScriptTest {

    static {
        File cfgFile = new File( TraderHomeHelper.class.getClassLoader().getResource("etc/trader-groovy.xml").getFile());
        TraderHomeHelper.init(cfgFile);
    }

    @Test
    public void test() throws Exception
    {
        Exchangeable e = Exchangeable.fromString("ru1901");
        LocalDate tradingDay = LocalDate.of(2018,  Month.DECEMBER, 3);

        BeansContainer beansContainer = initBeans(e, tradingDay);
    }

    private static BeansContainer initBeans(Exchangeable e, LocalDate tradingDay) throws Exception
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
