package trader.service.trade;

import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ScheduledExecutorService;

import org.junit.Test;

import trader.common.beans.BeansContainer;
import trader.common.exchangeable.Exchange;
import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.MarketDayUtil;
import trader.common.util.PriceUtil;
import trader.common.util.TraderHomeUtil;
import trader.service.TraderHomeHelper;
import trader.service.data.KVStoreService;
import trader.service.md.MarketData;
import trader.service.md.MarketDataService;
import trader.simulator.SimBeansContainer;
import trader.simulator.SimKVStoreService;
import trader.simulator.SimMarketDataService;
import trader.simulator.SimMarketTimeService;
import trader.simulator.SimScheduledExecutorService;
import trader.simulator.trade.SimTxnSession;
import trader.simulator.trade.SimTxnSessionFactory;

public class AccountTest implements TradeConstants {
    static {
        TraderHomeHelper.init();
    }

    @Test
    public void test() throws Exception
    {
        LocalDateTime beginTime = LocalDateTime.of(2018, Month.DECEMBER, 28, 8, 50);
        LocalDateTime endTime = LocalDateTime.of(2018, Month.DECEMBER, 28, 15, 04);
        Exchangeable au1906 = Exchangeable.fromString("au1906");
        LocalDate tradingDay = MarketDayUtil.getTradingDay(Exchange.SHFE, beginTime);

        SimBeansContainer beansContainer = new SimBeansContainer();
        SimMarketTimeService mtService = new SimMarketTimeService();
        SimKVStoreService kvStoreService = new SimKVStoreService();
        SimMarketDataService mdService = new SimMarketDataService();
        SimScheduledExecutorService scheduledExecutorService = new SimScheduledExecutorService();

        beansContainer.addBean(MarketDataService.class, mdService);
        beansContainer.addBean(MarketTimeService.class, mtService);
        beansContainer.addBean(KVStoreService.class, kvStoreService);
        beansContainer.addBean(ScheduledExecutorService.class, scheduledExecutorService);

        scheduledExecutorService.init(beansContainer);


        mtService.setTimeRange(tradingDay, beginTime, endTime);

        mdService.addSubscriptions(Arrays.asList(new Exchangeable[] {au1906}));
        mdService.init(beansContainer);

        final OrderRefGenImpl orderRefGen = new OrderRefGenImpl(beansContainer);
        final Map<String, TxnSessionFactory> txnSessionFactories = new TreeMap<>();
        txnSessionFactories.put(TxnSession.PROVIDER_SIM, new SimTxnSessionFactory());

        TradeService tradeService = new TradeService() {

            @Override
            public void init(BeansContainer beansContainer) throws Exception {
            }

            @Override
            public void destroy() {

            }

            @Override
            public OrderRefGen getOrderRefGen() {
                return orderRefGen;
            }

            @Override
            public Account getPrimaryAccount() {
                return null;
            }

            @Override
            public Account getAccount(String id) {
                return null;
            }

            @Override
            public Collection<Account> getAccounts() {
                return null;
            }

            @Override
            public Map<String, TxnSessionFactory> getTxnSessionFactories() {
                return txnSessionFactories;
            }

        };

        Map config = new HashMap<>();
        String text =
                "[connectionProps]\n"+
                "initMoney=500000.00\n" +
                "commissionsFile="+TraderHomeUtil.getTraderHome()+"/etc/sim-account1.commissions.json";
        config.put("id", "sim-account1");
        config.put("provider", TxnSession.PROVIDER_SIM);
        config.put("text", text);
        AccountImpl account = new AccountImpl(tradeService, beansContainer, config);

        //创建模拟交易连接
        SimTxnSession txnSession = (SimTxnSession)account.getSession();
        txnSession.connect(account.getConnectionProps());
        mtService.addListener(txnSession);

        //到9:01:00
        while(mtService.nextTimePiece()) {
            LocalDateTime time = mtService.getMarketTime();
            if ( time.getHour()==9 && time.getMinute()==1 ) {
                break;
            }
        }
        MarketData md = mdService.getLastData(au1906);
        assertTrue(md!=null);

        //创建Order
        OrderBuilder odrBuilder = new OrderBuilder()
                .setDirection(OrderDirection.Buy)
                .setExchagneable(au1906)
                .setLimitPrice(md.lastPrice-10000)
                .setPriceType(OrderPriceType.LimitPrice)
                .setOffsetFlag(OrderOffsetFlag.OPEN)
                ;
        //确认报单前模拟账户资产数据
        assertTrue(account.getMoney(AccMoney_Available)==PriceUtil.price2long(500000));
        //开始报单
        Order order = account.createOrder(odrBuilder);
        Position pos = account.getPosition(au1906);
        //确认报单后本地冻结
        assertTrue(order.getStateTuple().getState()==OrderState.Submitted);
        assertTrue(account.getMoney(AccMoney_Available)!=PriceUtil.price2long(500000));
        assertTrue(account.getMoney(AccMoney_FrozenMargin)!=0);
        assertTrue(account.getMoney(AccMoney_FrozenCommission)!=0);
        assertTrue(order.getMoney(OdrMoney_LocalFrozenMargin)!=0);
        assertTrue(order.getMoney(OdrMoney_LocalFrozenCommission)!=0);
        assertTrue(order.getMoney(OdrMoney_LocalFrozenMargin) == pos.getMoney(PosMoney_FrozenMargin));
        assertTrue(order.getMoney(OdrMoney_LocalFrozenCommission) == pos.getMoney(PosMoney_FrozenCommission));
        //下一时间片, 报单确认
        mtService.nextTimePiece();
        assertTrue(order.getStateTuple().getState()==OrderState.Accepted);
    }

}
