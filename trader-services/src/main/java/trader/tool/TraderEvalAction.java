package trader.tool;

import java.io.PrintWriter;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

import trader.common.beans.BeansContainer;
import trader.common.beans.Lifecycle;
import trader.common.exchangeable.Exchange;
import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableTradingTimes;
import trader.common.exchangeable.MarketDayUtil;
import trader.common.util.DateUtil;
import trader.common.util.PriceUtil;
import trader.common.util.StringUtil;
import trader.common.util.StringUtil.KVPair;
import trader.service.concurrent.OrderedExecutor;
import trader.service.data.KVStoreService;
import trader.service.md.MarketDataService;
import trader.service.ta.TechnicalAnalysisServiceImpl;
import trader.service.trade.Account;
import trader.service.trade.MarketTimeService;
import trader.service.trade.Order;
import trader.service.trade.TradeConstants.AccMoney;
import trader.service.trade.TradeConstants.OdrVolume;
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

/**
 * 回测
 */
public class TraderEvalAction implements CmdAction {
    protected PrintWriter writer;
    protected LocalDate beginDate;
    protected LocalDate endDate;
    protected Exchangeable instrument;

    @Override
    public String getCommand() {
        return "eval";
    }

    @Override
    public void usage(PrintWriter writer) {
        writer.println("eval -Dtrader.configFile=TRADE_XML --instrument=INSTRUMENT --beginDate=YYYYMMDD --endDate=YYYYMMDD");
        writer.println("\t回测");
    }

    @Override
    public int execute(BeansContainer beansContainer, PrintWriter writer, List<KVPair> options) throws Exception
    {
        this.writer = writer;
        if ( !parseOptions(options)) {
            return 1;
        }
        SimpleBeansContainer globalBeans = createGlobalBeans();
        LocalDate tradingDay = beginDate;
        if ( !MarketDayUtil.isMarketDay(Exchange.SHFE, tradingDay)) {
            tradingDay = MarketDayUtil.nextMarketDay(Exchange.SHFE, tradingDay);
        }
        while(!tradingDay.isAfter(endDate)) {
            SimpleBeansContainer beans = getBeansFor(globalBeans, instrument, tradingDay);
            doTrade(beans);
            destroyBeans(globalBeans, beans);
            tradingDay = MarketDayUtil.nextMarketDay(instrument.exchange(), tradingDay);
        }
        return 0;
    }

    private boolean parseOptions(List<KVPair> options) {
        beginDate = null;
        for(KVPair kv:options) {
            if ( StringUtil.isEmpty(kv.v)) {
                continue;
            }
            switch(kv.k.toLowerCase()) {
            case "begindate":
                beginDate = DateUtil.str2localdate(kv.v);
                break;
            case "enddate":
                endDate = DateUtil.str2localdate(kv.v);
                break;
            case "instrument":
                instrument = Exchangeable.fromString(kv.v);
                break;
            }
        }
        if ( endDate==null && beginDate==null ) {
            writer.println("需要提供过滤参数");
            return false;
        }
        return true;
    }

    private void doTrade(SimpleBeansContainer beansContainer) {
        SimMarketTimeService mtService = beansContainer.getBean(SimMarketTimeService.class);
        //时间片段循环
        while(mtService.nextTimePiece());

        TradeService tradeService = beansContainer.getBean(TradeService.class);
        Account account = tradeService.getPrimaryAccount();
        List<Order> orders = account.getOrders();
        writer.println("--- 交易日 "+DateUtil.date2str(mtService.getTradingDay())+" ---");
        writer.println("报单:");
        for(Order order:orders) {
            String orderLine = String.format( "%12s %6s %8s %4d",
                    order.getRef(),
                    order.getDirection(),
                    PriceUtil.long2str(order.getLimitPrice()),
                    order.getVolume(OdrVolume.ReqVolume));
            writer.println( orderLine );
        }
        String accountLine = String.format("账户 净值: %8s, 保证金: %8s",
                PriceUtil.long2str(account.getMoney(AccMoney.Balance)),
                PriceUtil.long2str(account.getMoney(AccMoney.CurrMargin))
                );
    }

    /**
     * 创建一些跨越交易日的服务
     */
    private SimpleBeansContainer createGlobalBeans() {
        SimpleBeansContainer globalBeans = new SimpleBeansContainer();

        SimKVStoreService kvStoreService = new SimKVStoreService();
        globalBeans.addBean(KVStoreService.class, kvStoreService);
        return globalBeans;
    }

    /**
     * 为某个交易日创建运行环境
     */
    private SimpleBeansContainer getBeansFor(SimpleBeansContainer globalBeans, Exchangeable instrument, LocalDate tradingDay)
            throws Exception
    {
        SimpleBeansContainer beansContainer = new SimpleBeansContainer();
        SimMarketTimeService mtService = new SimMarketTimeService();
        SimOrderedExecutor orderedExecutor = new SimOrderedExecutor();
        SimScheduledExecutorService scheduledExecutorService = new SimScheduledExecutorService();
        SimMarketDataService mdService = new SimMarketDataService();
        SimTradeService tradeService = new SimTradeService();
        TechnicalAnalysisServiceImpl taService = new TechnicalAnalysisServiceImpl();
        SimTradletService tradletService = new SimTradletService();

        beansContainer.addBean(MarketTimeService.class, mtService);
        beansContainer.addBean(OrderedExecutor.class, orderedExecutor);
        beansContainer.addBean(ScheduledExecutorService.class, scheduledExecutorService);
        beansContainer.addBean(MarketDataService.class, mdService);
        beansContainer.addBean(TradeService.class, tradeService);
        beansContainer.addBean(TechnicalAnalysisServiceImpl.class, taService);
        beansContainer.addBean(TradletService.class, tradletService);

        scheduledExecutorService.init(beansContainer);
        ExchangeableTradingTimes tradingTimes = instrument.exchange().getTradingTimes(instrument, tradingDay);
        mtService.setTimeRanges(tradingDay, tradingTimes.getMarketTimes() );
        mdService.init(beansContainer);
        taService.init(beansContainer);
        tradeService.init(beansContainer);
        tradletService.init(beansContainer);
        return beansContainer;
    }

    private void destroyBeans(SimpleBeansContainer globalBeans, SimpleBeansContainer beans) {
        for(Class beanClass:beans.getAllBeans().keySet()) {
            if ( globalBeans.getBean(beanClass)!=null ) {
                continue;
            }
            Object bean = beans.getBean(beanClass);
            if ( bean instanceof Lifecycle ) {
                ((Lifecycle)bean).destroy();
            }
        }
    }

}
