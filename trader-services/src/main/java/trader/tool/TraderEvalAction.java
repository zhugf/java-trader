package trader.tool;

import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
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
import trader.service.md.MarketDataService;
import trader.service.plugin.PluginService;
import trader.service.plugin.PluginServiceImpl;
import trader.service.ta.BarServiceImpl;
import trader.service.trade.Account;
import trader.service.trade.MarketTimeService;
import trader.service.trade.Order;
import trader.service.trade.TradeConstants.AccMoney;
import trader.service.trade.TradeConstants.OdrVolume;
import trader.service.trade.TradeService;
import trader.service.tradlet.TradletService;
import trader.service.util.CmdAction;
import trader.service.util.SimpleBeansContainer;
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
    protected List<Exchangeable> instruments = new ArrayList<>();
    protected Exchangeable mdInstrument = null;

    @Override
    public String getCommand() {
        return "eval";
    }

    @Override
    public void usage(PrintWriter writer) {
        writer.println("eval -Dtrader.configFile=TRADE_XML --beginDate=YYYYMMDD --endDate=YYYYMMDD [--instruments=INSTRUMENT1,INSTRUMENT2]");
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
            SimpleBeansContainer beans = getBeansFor(globalBeans, instruments, tradingDay);
            doTrade(beans);
            destroyBeans(globalBeans, beans);
            tradingDay = MarketDayUtil.nextMarketDay(mdInstrument.exchange(), tradingDay);
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
                instruments.add(Exchangeable.fromString(kv.v));
                break;
            case "instruments":
                for(String instrument:StringUtil.split(kv.v, ",")) {
                    instruments.add(Exchangeable.fromString(instrument));
                }
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
            LocalDateTime stateTime = DateUtil.long2datetime(order.getStateTuple().getTimestamp());
            String orderLine = String.format( "%12s %6s %8s %8s %4d %8s %12s",
                    order.getRef(),
                    order.getDirection(),
                    order.getOffsetFlags(),
                    PriceUtil.long2str(order.getLimitPrice()),
                    order.getVolume(OdrVolume.ReqVolume),
                    order.getStateTuple().getState(),
                    DateUtil.date2str(stateTime)
                    );
            writer.println( orderLine );
        }
        String accountLine = String.format("账户净值: %8s 保证金: %8s 手续费: %8s 平仓盈亏: %8s",
                PriceUtil.long2str(account.getMoney(AccMoney.Balance)),
                PriceUtil.long2str(account.getMoney(AccMoney.CurrMargin)),
                PriceUtil.long2str(account.getMoney(AccMoney.Commission)),
                PriceUtil.long2str(account.getMoney(AccMoney.CloseProfit))
                );
        writer.println(accountLine);
    }

    /**
     * 创建一些跨越交易日的服务
     */
    private SimpleBeansContainer createGlobalBeans() throws Exception
    {
        SimpleBeansContainer globalBeans = new SimpleBeansContainer();

        PluginServiceImpl pluginService = new PluginServiceImpl();
        pluginService.init();
        globalBeans.addBean(PluginService.class, pluginService);
        return globalBeans;
    }

    /**
     * 为某个交易日创建运行环境
     */
    private SimpleBeansContainer getBeansFor(SimpleBeansContainer globalBeans, List<Exchangeable> instruments, LocalDate tradingDay)
            throws Exception
    {
        SimpleBeansContainer beansContainer = new SimpleBeansContainer(globalBeans);
        SimMarketTimeService mtService = new SimMarketTimeService();
        SimOrderedExecutor orderedExecutor = new SimOrderedExecutor();
        SimScheduledExecutorService scheduledExecutorService = new SimScheduledExecutorService();
        SimMarketDataService mdService = new SimMarketDataService();
        SimTradeService tradeService = new SimTradeService();
        BarServiceImpl taService = new BarServiceImpl();
        SimTradletService tradletService = new SimTradletService();

        beansContainer.addBean(MarketTimeService.class, mtService);
        beansContainer.addBean(OrderedExecutor.class, orderedExecutor);
        beansContainer.addBean(ScheduledExecutorService.class, scheduledExecutorService);
        beansContainer.addBean(MarketDataService.class, mdService);
        beansContainer.addBean(TradeService.class, tradeService);
        beansContainer.addBean(BarServiceImpl.class, taService);
        beansContainer.addBean(TradletService.class, tradletService);

        scheduledExecutorService.init(beansContainer);
        mdService.addSubscriptions(instruments);
        mdService.init(beansContainer);
        Collection<Exchangeable> mdInstruments = mdService.getSubscriptions();
        mdInstrument = mdInstruments.iterator().next();
        ExchangeableTradingTimes tradingTimes = mdInstrument.exchange().getTradingTimes(mdInstrument, tradingDay);
        mtService.setTimeRanges(tradingDay, tradingTimes.getMarketTimes() );
        //taService.init(beansContainer);
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
