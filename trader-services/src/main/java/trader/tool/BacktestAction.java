package trader.tool;

import java.io.PrintWriter;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

import trader.common.beans.BeansContainer;
import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableTradingTimes;
import trader.common.util.StringUtil.KVPair;
import trader.service.concurrent.OrderedExecutor;
import trader.service.data.KVStoreService;
import trader.service.md.MarketDataService;
import trader.service.ta.TechnicalAnalysisServiceImpl;
import trader.service.trade.MarketTimeService;
import trader.service.trade.TradeService;
import trader.service.tradlet.TradletService;
import trader.service.util.CmdAction;
import trader.service.util.SimpleBeansContainer;
import trader.simulator.SimKVStoreService;
import trader.simulator.SimMarketDataService;
import trader.simulator.SimMarketTimeService;
import trader.simulator.SimOrderedExecutor;
import trader.simulator.SimScheduledExecutorService;
import trader.simulator.SimTradletService;
import trader.simulator.trade.SimTradeService;

public class BacktestAction implements CmdAction {

    @Override
    public String getCommand() {
        return "backtest";
    }

    @Override
    public void usage(PrintWriter writer) {
        writer.println("backtest <TRADE_XML> <DATE_RANGE>");
        writer.println("\t回测");
    }

    @Override
    public int execute(BeansContainer beansContainer, PrintWriter writer, List<KVPair> options) throws Exception
    {
        // TODO Auto-generated method stub
        return 0;
    }

    /**
     * 为某个交易日创建运行环境
     */
    private BeansContainer initBeans(Exchangeable e, LocalDate tradingDay)
            throws Exception
    {
        SimpleBeansContainer beansContainer = new SimpleBeansContainer();
        SimMarketTimeService mtService = new SimMarketTimeService();
        SimOrderedExecutor orderedExecutor = new SimOrderedExecutor();
        SimScheduledExecutorService scheduledExecutorService = new SimScheduledExecutorService();
        SimMarketDataService mdService = new SimMarketDataService();
        SimKVStoreService kvStoreService = new SimKVStoreService();
        SimTradeService tradeService = new SimTradeService();
        TechnicalAnalysisServiceImpl taService = new TechnicalAnalysisServiceImpl();
        SimTradletService tradletService = new SimTradletService();

        beansContainer.addBean(MarketTimeService.class, mtService);
        beansContainer.addBean(OrderedExecutor.class, orderedExecutor);
        beansContainer.addBean(ScheduledExecutorService.class, scheduledExecutorService);
        beansContainer.addBean(MarketDataService.class, mdService);
        beansContainer.addBean(KVStoreService.class, kvStoreService);
        beansContainer.addBean(TradeService.class, tradeService);
        beansContainer.addBean(TechnicalAnalysisServiceImpl.class, taService);
        beansContainer.addBean(TradletService.class, tradletService);

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
