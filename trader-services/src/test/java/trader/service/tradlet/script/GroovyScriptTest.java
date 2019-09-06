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
import trader.service.log.LogServiceImpl;
import trader.service.md.MarketDataService;
import trader.service.ta.TechnicalAnalysisServiceImpl;
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
 * 测试GROOVY脚本能力
 */
public class GroovyScriptTest {

    static {
        LogServiceImpl.setLogLevel("trader.service", "INFO");
        LogServiceImpl.setLogLevel("org.apache.commons", "INFO");

        File cfgFile = new File( TraderHomeHelper.class.getClassLoader().getResource("etc/trader-groovy.xml").getFile());
        TraderHomeHelper.init(cfgFile);
    }

    @Test
    public void test() throws Exception
    {
        Exchangeable e = Exchangeable.fromString("ru1901");
        LocalDate tradingDay = LocalDate.of(2018,  Month.DECEMBER, 3);

        BeansContainer beansContainer = initBeans(e, tradingDay);
        SimMarketTimeService mtService = beansContainer.getBean(SimMarketTimeService.class);
        //时间片段循环
        while(mtService.nextTimePiece());
    }

    private static BeansContainer initBeans(Exchangeable e, LocalDate tradingDay) throws Exception
    {
        SimpleBeansContainer beansContainer = new SimpleBeansContainer();
        SimMarketTimeService mtService = new SimMarketTimeService();
        SimScheduledExecutorService scheduledExecutorService = new SimScheduledExecutorService();
        SimMarketDataService mdService = new SimMarketDataService();
        SimKVStoreService kvStoreService = new SimKVStoreService();
        SimTradeService tradeService = new SimTradeService();
        TechnicalAnalysisServiceImpl taService = new TechnicalAnalysisServiceImpl();
        SimTradletService tradletService = new SimTradletService();

        beansContainer.addBean(MarketTimeService.class, mtService);
        beansContainer.addBean(ScheduledExecutorService.class, scheduledExecutorService);
        beansContainer.addBean(MarketDataService.class, mdService);
        beansContainer.addBean(KVStoreService.class, kvStoreService);
        beansContainer.addBean(TradeService.class, tradeService);
        beansContainer.addBean(TechnicalAnalysisServiceImpl.class, taService);
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
