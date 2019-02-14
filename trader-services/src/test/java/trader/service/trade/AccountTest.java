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

import org.junit.Before;
import org.junit.Test;

import trader.common.beans.BeansContainer;
import trader.common.exception.AppException;
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

@SuppressWarnings({"unchecked", "unused", "rawtypes"})
public class AccountTest implements TradeConstants {
    static {
        TraderHomeHelper.init();
    }

    LocalDateTime beginTime = LocalDateTime.of(2018, Month.DECEMBER, 28, 8, 50);
    LocalDateTime endTime = LocalDateTime.of(2018, Month.DECEMBER, 28, 15, 04);
    Exchangeable au1906 = Exchangeable.fromString("au1906");
    LocalDate tradingDay = au1906.exchange().detectTradingTimes(au1906, beginTime).getTradingDay();

    SimBeansContainer beansContainer;
    SimMarketTimeService mtService;
    SimKVStoreService kvStoreService;
    SimMarketDataService mdService;
    SimScheduledExecutorService scheduledExecutorService;
    AccountImpl account;

    @Before
    public void testInit() throws Exception {
        beansContainer = new SimBeansContainer();
        mtService = new SimMarketTimeService();
        SimKVStoreService kvStoreService = new SimKVStoreService();
        mdService = new SimMarketDataService();
        scheduledExecutorService = new SimScheduledExecutorService();

        beansContainer.addBean(MarketDataService.class, mdService);
        beansContainer.addBean(MarketTimeService.class, mtService);
        beansContainer.addBean(KVStoreService.class, kvStoreService);
        beansContainer.addBean(ScheduledExecutorService.class, scheduledExecutorService);

        scheduledExecutorService.init(beansContainer);

        mtService.setTimeRanges(tradingDay, new LocalDateTime[][]{ {beginTime, endTime}} );

        mdService.addSubscriptions(Arrays.asList(new Exchangeable[] {au1906}));
        mdService.init(beansContainer);

        TradeServiceTest tradeService = new TradeServiceTest(beansContainer);

        Map config = new HashMap<>();
        String text =
                "[connectionProps]\n"+
                "initMoney=500000.00\n" +
                "commissionsFile="+TraderHomeUtil.getTraderHome()+"/etc/sim-account1.commissions.json";
        config.put("id", "sim-account1");
        config.put("provider", TxnSession.PROVIDER_SIM);
        config.put("text", text);
        account = new AccountImpl(tradeService, beansContainer, config);

        mdService.addListener(account);
        //创建模拟交易连接
        SimTxnSession txnSession = (SimTxnSession)account.getSession();
        txnSession.connect(account.getConnectionProps());
        mtService.addListener(txnSession);

    }

    @Test
    public void testLong() throws Exception
    {

        //到9:01:00
        while(mtService.nextTimePiece()) {
            LocalDateTime time = mtService.getMarketTime();
            if ( time.getHour()==9 && time.getMinute()==1 ) {
                break;
            }
        }
        //确认报单前模拟账户资产数据
        assertTrue(account.getMoney(AccMoney_Available)==PriceUtil.price2long(500000));
        //测试报单取消
        {
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
            account.cancelOrder(order.getRef());
            assertTrue(order.getStateTuple().getState()==OrderState.Accepted && order.getStateTuple().getSubmitState()==OrderSubmitState.CancelSubmitted);

            assertTrue(pos.getMoney(PosMoney_FrozenMargin)!=0);
            assertTrue(pos.getMoney(PosMoney_FrozenCommission)!=0);
            //下一时间片, 报单取消
            mtService.nextTimePiece();
            assertTrue(order.getStateTuple().getState()==OrderState.Canceled && order.getStateTuple().getSubmitState()==OrderSubmitState.Accepted);
            //确认冻结保证金和手续费回退: account, position, order
            assertTrue(account.getMoney(AccMoney_Available)==account.getMoney(AccMoney_Balance));
            assertTrue(account.getMoney(AccMoney_FrozenMargin)==0);
            assertTrue(account.getMoney(AccMoney_FrozenCommission)==0);

            assertTrue(pos.getMoney(PosMoney_FrozenMargin)==0);
            assertTrue(pos.getMoney(PosMoney_FrozenCommission)==0);

            assertTrue(order.getMoney(OdrMoney_LocalFrozenMargin)==order.getMoney(OdrMoney_LocalUnfrozenMargin));
            assertTrue(order.getMoney(OdrMoney_LocalFrozenCommission)==order.getMoney(OdrMoney_LocalUnfrozenCommission));
        }
        //测试报单成交
        {
            MarketData md = mdService.getLastData(au1906);
            assertTrue(md!=null);
            long openPrice = md.lastPrice+10000;
            //创建Order
            OrderBuilder odrBuilder = new OrderBuilder()
                    .setDirection(OrderDirection.Buy)
                    .setExchagneable(au1906)
                    .setLimitPrice(openPrice)
                    .setPriceType(OrderPriceType.LimitPrice)
                    .setOffsetFlag(OrderOffsetFlag.OPEN)
                    ;
            //开始报单
            Order order = account.createOrder(odrBuilder);
            Position pos = account.getPosition(au1906);
            assertTrue(pos.getDirection()==PosDirection.Net);
            //确认报单后本地冻结
            assertTrue(order.getStateTuple().getState()==OrderState.Submitted);
            assertTrue(account.getMoney(AccMoney_Available)!=PriceUtil.price2long(500000));
            assertTrue(account.getMoney(AccMoney_FrozenMargin)!=0);
            assertTrue(account.getMoney(AccMoney_FrozenCommission)!=0);
            assertTrue(order.getMoney(OdrMoney_LocalFrozenMargin)!=0);
            assertTrue(order.getMoney(OdrMoney_LocalFrozenCommission)!=0);
            assertTrue(order.getMoney(OdrMoney_LocalFrozenMargin) == pos.getMoney(PosMoney_FrozenMargin));
            assertTrue(order.getMoney(OdrMoney_LocalFrozenCommission) == pos.getMoney(PosMoney_FrozenCommission));
            assertTrue(pos.getActiveOrders().size()>0);

            //下一时间片, 报单确认
            mtService.nextTimePiece();
            assertTrue(order.getStateTuple().getState()==OrderState.Accepted);
            int openVolumeBefore = pos.getVolume(PosVolume_OpenVolume);
            //下一个行情切片, 成交
            while(mdService.getLastData(au1906).lastPrice==md.lastPrice) {
                mtService.nextTimePiece();
            }
            //检测开仓后状态
            assertTrue(order.getStateTuple().getState()==OrderState.Complete);
            assertTrue(order.getVolume(OdrVolume_TradeVolume)==order.getVolume(OdrVolume_ReqVolume));
            assertTrue(order.getMoney(OdrMoney_LocalFrozenMargin)==order.getMoney(OdrMoney_LocalUnfrozenMargin));
            assertTrue(order.getMoney(OdrMoney_LocalFrozenCommission)==order.getMoney(OdrMoney_LocalUnfrozenCommission));
            assertTrue(order.getMoney(OdrMoney_LocalUsedMargin)!=0);
            assertTrue(order.getMoney(OdrMoney_LocalUsedCommission)!=0);
            assertTrue(order.getMoney(OdrMoney_OpenCost)==openPrice);

            assertTrue(account.getMoney(AccMoney_FrozenMargin)==0);
            assertTrue(account.getMoney(AccMoney_FrozenCommission)==0);
            assertTrue(account.getMoney(AccMoney_CurrMargin)!=0);
            assertTrue(account.getMoney(AccMoney_Commission)!=0);
            assertTrue(account.getMoney(AccMoney_Available) < account.getMoney(AccMoney_Balance));

            assertTrue(pos.getVolume(PosVolume_OpenVolume)==1+openVolumeBefore);
            assertTrue(pos.getVolume(PosVolume_Position)==1);
            assertTrue(pos.getVolume(PosVolume_LongPosition)==1);
            assertTrue(pos.getVolume(PosVolume_TodayPosition)==1);
            assertTrue(pos.getVolume(PosVolume_LongTodayPosition)==1);
            assertTrue(pos.getMoney(PosMoney_FrozenMargin)==0);
            assertTrue(pos.getMoney(PosMoney_UseMargin)!=0);
            assertTrue(pos.getMoney(PosMoney_PositionProfit)!=0);
            assertTrue(pos.getMoney(PosMoney_OpenCost)!=0);
            assertTrue(pos.getDirection()==PosDirection.Long);
            assertTrue(pos.getActiveOrders().size()==0);
        }
        //测试报单平仓
        {
            Position pos = account.getPosition(au1906);
            long accAvail0 = account.getMoney(AccMoney_Available);
            long posProfit0 = pos.getMoney(PosMoney_PositionProfit);

            //到9:30:00
            while(mtService.nextTimePiece()) {
                LocalDateTime time = mtService.getMarketTime();
                if ( time.getHour()==9 && time.getMinute()==30 ) {
                    break;
                }
            }

            assertTrue(pos.getMoney(PosMoney_PositionProfit)!=posProfit0);
            assertTrue(account.getMoney(AccMoney_Available)!=accAvail0);

            MarketData md = mdService.getLastData(au1906);
            assertTrue(md!=null);
            long closePrice = md.lastBidPrice();
            { //检验报单数量超过可用数量的场景
                //创建Order
                OrderBuilder odrBuilder = new OrderBuilder()
                        .setDirection(OrderDirection.Sell)
                        .setExchagneable(au1906)
                        .setLimitPrice(closePrice)
                        .setPriceType(OrderPriceType.LimitPrice)
                        .setOffsetFlag(OrderOffsetFlag.CLOSE_TODAY)
                        .setVolume(2)
                        ;
                //开始报单
                try {
                    Order order = account.createOrder(odrBuilder);
                    assertTrue(false);
                }catch(AppException e) {}
                assertTrue(account.getMoney(AccMoney_FrozenCommission)==0);
                assertTrue(account.getMoney(AccMoney_FrozenMargin)==0);
                assertTrue(pos.getVolume(PosVolume_LongFrozen)==0);
            }

            long accCommission0 = account.getMoney(AccMoney_Commission);
            //创建Order
            OrderBuilder odrBuilder = new OrderBuilder()
                    .setDirection(OrderDirection.Sell)
                    .setExchagneable(au1906)
                    .setLimitPrice(closePrice)
                    .setPriceType(OrderPriceType.LimitPrice)
                    .setOffsetFlag(OrderOffsetFlag.CLOSE_TODAY)
                    ;
            //开始报单
            Order order = account.createOrder(odrBuilder);
            //确认报单后本地冻结可用手数
            assertTrue(order.getStateTuple().getState()==OrderState.Submitted);
            assertTrue(pos.getVolume(PosVolume_LongFrozen)!=0 && pos.getVolume(PosVolume_ShortFrozen)==0);

            //下一时间片, 报单确认
            mtService.nextTimePiece();

            assertTrue(order.getStateTuple().getState()==OrderState.Accepted || order.getStateTuple().getState().isDone());
            if ( !order.getStateTuple().getState().isDone() ) {
                //下一个行情切片, 成交
                while(mdService.getLastData(au1906).lastPrice==md.lastPrice) {
                    mtService.nextTimePiece();
                }
            }
            assertTrue(!order.getTransactions().isEmpty());
            assertTrue(order.getVolume(OdrVolume_TradeVolume)==order.getVolume(OdrVolume_ReqVolume));

            assertTrue(pos.getVolume(PosVolume_Position)==0);
            assertTrue(pos.getVolume(PosVolume_CloseVolume)==1);
            assertTrue(pos.getDirection()==PosDirection.Net);
            assertTrue(pos.getMoney(PosMoney_PositionCost)==0);
            assertTrue(pos.getMoney(PosMoney_UseMargin)==0);
            assertTrue(pos.getMoney(PosMoney_CloseProfit)!=0);
            assertTrue(pos.getMoney(PosMoney_OpenCost)==0);

            assertTrue(account.getMoney(AccMoney_Commission)!=accCommission0);
            assertTrue(account.getMoney(AccMoney_CloseProfit)!=0);
            assertTrue(account.getMoney(AccMoney_CurrMargin)==0);
            assertTrue(account.getMoney(AccMoney_PositionProfit)==0);
            assertTrue(account.getMoney(AccMoney_FrozenMargin)==0);
            assertTrue(account.getMoney(AccMoney_FrozenCommission)==0);
        }

    }



    @Test
    public void testShort() throws Exception
    {


        //到9:01:00
        while(mtService.nextTimePiece()) {
            LocalDateTime time = mtService.getMarketTime();
            if ( time.getHour()==9 && time.getMinute()==1 ) {
                break;
            }
        }
        //确认报单前模拟账户资产数据
        assertTrue(account.getMoney(AccMoney_Available)==PriceUtil.price2long(500000));
        //测试报单取消
        {
            MarketData md = mdService.getLastData(au1906);
            assertTrue(md!=null);

            //创建Order
            OrderBuilder odrBuilder = new OrderBuilder()
                    .setDirection(OrderDirection.Sell)
                    .setExchagneable(au1906)
                    .setLimitPrice(md.lastPrice+10000)
                    .setPriceType(OrderPriceType.LimitPrice)
                    .setOffsetFlag(OrderOffsetFlag.OPEN)
                    ;
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
            account.cancelOrder(order.getRef());
            assertTrue(order.getStateTuple().getState()==OrderState.Accepted && order.getStateTuple().getSubmitState()==OrderSubmitState.CancelSubmitted);

            assertTrue(pos.getMoney(PosMoney_FrozenMargin)!=0);
            assertTrue(pos.getMoney(PosMoney_FrozenCommission)!=0);
            //下一时间片, 报单取消
            mtService.nextTimePiece();
            assertTrue(order.getStateTuple().getState()==OrderState.Canceled && order.getStateTuple().getSubmitState()==OrderSubmitState.Accepted);
            //确认冻结保证金和手续费回退: account, position, order
            assertTrue(account.getMoney(AccMoney_Available)==account.getMoney(AccMoney_Balance));
            assertTrue(account.getMoney(AccMoney_FrozenMargin)==0);
            assertTrue(account.getMoney(AccMoney_FrozenCommission)==0);

            assertTrue(pos.getMoney(PosMoney_FrozenMargin)==0);
            assertTrue(pos.getMoney(PosMoney_FrozenCommission)==0);

            assertTrue(order.getMoney(OdrMoney_LocalFrozenMargin)==order.getMoney(OdrMoney_LocalUnfrozenMargin));
            assertTrue(order.getMoney(OdrMoney_LocalFrozenCommission)==order.getMoney(OdrMoney_LocalUnfrozenCommission));
        }
        //测试报单成交
        {
            MarketData md = mdService.getLastData(au1906);
            assertTrue(md!=null);
            long openPrice = md.lastPrice-10000;
            //创建Order
            OrderBuilder odrBuilder = new OrderBuilder()
                    .setDirection(OrderDirection.Sell)
                    .setExchagneable(au1906)
                    .setLimitPrice(openPrice)
                    .setPriceType(OrderPriceType.LimitPrice)
                    .setOffsetFlag(OrderOffsetFlag.OPEN)
                    ;
            //开始报单
            Order order = account.createOrder(odrBuilder);
            Position pos = account.getPosition(au1906);
            assertTrue(pos.getDirection()==PosDirection.Net);
            //确认报单后本地冻结
            assertTrue(order.getStateTuple().getState()==OrderState.Submitted);
            assertTrue(account.getMoney(AccMoney_Available)!=PriceUtil.price2long(500000));
            assertTrue(account.getMoney(AccMoney_FrozenMargin)!=0);
            assertTrue(account.getMoney(AccMoney_FrozenCommission)!=0);
            assertTrue(order.getMoney(OdrMoney_LocalFrozenMargin)!=0);
            assertTrue(order.getMoney(OdrMoney_LocalFrozenCommission)!=0);
            assertTrue(order.getMoney(OdrMoney_LocalFrozenMargin) == pos.getMoney(PosMoney_FrozenMargin));
            assertTrue(order.getMoney(OdrMoney_LocalFrozenCommission) == pos.getMoney(PosMoney_FrozenCommission));
            assertTrue(pos.getActiveOrders().size()>0);

            //下一时间片, 报单确认
            mtService.nextTimePiece();
            assertTrue(order.getStateTuple().getState()==OrderState.Accepted);
            int openVolumeBefore = pos.getVolume(PosVolume_OpenVolume);
            //下一个行情切片, 成交
            while(mdService.getLastData(au1906).lastPrice==md.lastPrice) {
                mtService.nextTimePiece();
            }
            //检测开仓后状态
            assertTrue(order.getStateTuple().getState()==OrderState.Complete);
            assertTrue(order.getVolume(OdrVolume_TradeVolume)==order.getVolume(OdrVolume_ReqVolume));
            assertTrue(order.getMoney(OdrMoney_LocalFrozenMargin)==order.getMoney(OdrMoney_LocalUnfrozenMargin));
            assertTrue(order.getMoney(OdrMoney_LocalFrozenCommission)==order.getMoney(OdrMoney_LocalUnfrozenCommission));
            assertTrue(order.getMoney(OdrMoney_LocalUsedMargin)!=0);
            assertTrue(order.getMoney(OdrMoney_LocalUsedCommission)!=0);
            assertTrue(order.getMoney(OdrMoney_OpenCost)==openPrice);

            assertTrue(account.getMoney(AccMoney_FrozenMargin)==0);
            assertTrue(account.getMoney(AccMoney_FrozenCommission)==0);
            assertTrue(account.getMoney(AccMoney_CurrMargin)!=0);
            assertTrue(account.getMoney(AccMoney_Commission)!=0);
            assertTrue(account.getMoney(AccMoney_Available) < account.getMoney(AccMoney_Balance));

            assertTrue(pos.getVolume(PosVolume_OpenVolume)==1+openVolumeBefore);
            assertTrue(pos.getVolume(PosVolume_Position)==1);
            assertTrue(pos.getVolume(PosVolume_ShortPosition)==1);
            assertTrue(pos.getVolume(PosVolume_TodayPosition)==1);
            assertTrue(pos.getVolume(PosVolume_ShortTodayPosition)==1);
            assertTrue(pos.getMoney(PosMoney_FrozenMargin)==0);
            assertTrue(pos.getMoney(PosMoney_UseMargin)!=0);
            assertTrue(pos.getMoney(PosMoney_PositionProfit)!=0);
            assertTrue(pos.getMoney(PosMoney_OpenCost)!=0);
            assertTrue(pos.getDirection()==PosDirection.Short);
            assertTrue(pos.getActiveOrders().size()==0);
        }
        //测试报单平仓
        {
            Position pos = account.getPosition(au1906);
            long accAvail0 = account.getMoney(AccMoney_Available);
            long posProfit0 = pos.getMoney(PosMoney_PositionProfit);

            //到9:30:00
            while(mtService.nextTimePiece()) {
                LocalDateTime time = mtService.getMarketTime();
                if ( time.getHour()==9 && time.getMinute()==30 ) {
                    break;
                }
            }

            assertTrue(pos.getMoney(PosMoney_PositionProfit)!=posProfit0);
            assertTrue(account.getMoney(AccMoney_Available)!=accAvail0);

            MarketData md = mdService.getLastData(au1906);
            assertTrue(md!=null);
            long closePrice = md.lastAskPrice();
            { //检验报单数量超过可用数量的场景
                //创建Order
                OrderBuilder odrBuilder = new OrderBuilder()
                        .setDirection(OrderDirection.Buy)
                        .setExchagneable(au1906)
                        .setLimitPrice(closePrice)
                        .setPriceType(OrderPriceType.LimitPrice)
                        .setOffsetFlag(OrderOffsetFlag.CLOSE_TODAY)
                        .setVolume(2)
                        ;
                //开始报单
                try {
                    Order order = account.createOrder(odrBuilder);
                    assertTrue(false);
                }catch(AppException e) {}
                assertTrue(account.getMoney(AccMoney_FrozenCommission)==0);
                assertTrue(account.getMoney(AccMoney_FrozenMargin)==0);
                assertTrue(pos.getVolume(PosVolume_ShortFrozen)==0);
            }

            long accCommission0 = account.getMoney(AccMoney_Commission);
            //创建Order
            OrderBuilder odrBuilder = new OrderBuilder()
                    .setDirection(OrderDirection.Buy)
                    .setExchagneable(au1906)
                    .setLimitPrice(closePrice)
                    .setPriceType(OrderPriceType.LimitPrice)
                    .setOffsetFlag(OrderOffsetFlag.CLOSE_TODAY)
                    ;
            //开始报单
            Order order = account.createOrder(odrBuilder);
            //确认报单后本地冻结可用手数
            assertTrue(order.getStateTuple().getState()==OrderState.Submitted);
            assertTrue(pos.getVolume(PosVolume_ShortFrozen)!=0 && pos.getVolume(PosVolume_LongFrozen)==0);

            //下一时间片, 报单确认
            mtService.nextTimePiece();

            assertTrue(order.getStateTuple().getState()==OrderState.Accepted || order.getStateTuple().getState().isDone());
            if ( !order.getStateTuple().getState().isDone() ) {
                //下一个行情切片, 成交
                while(mdService.getLastData(au1906).lastPrice==md.lastPrice) {
                    mtService.nextTimePiece();
                }
            }
            assertTrue(!order.getTransactions().isEmpty());
            assertTrue(order.getVolume(OdrVolume_TradeVolume)==order.getVolume(OdrVolume_ReqVolume));

            assertTrue(pos.getVolume(PosVolume_Position)==0);
            assertTrue(pos.getVolume(PosVolume_CloseVolume)==1);
            assertTrue(pos.getDirection()==PosDirection.Net);
            assertTrue(pos.getMoney(PosMoney_PositionCost)==0);
            assertTrue(pos.getMoney(PosMoney_UseMargin)==0);
            assertTrue(pos.getMoney(PosMoney_CloseProfit)!=0);
            assertTrue(pos.getMoney(PosMoney_OpenCost)==0);

            assertTrue(account.getMoney(AccMoney_Commission)!=accCommission0);
            assertTrue(account.getMoney(AccMoney_CloseProfit)!=0);
            assertTrue(account.getMoney(AccMoney_CurrMargin)==0);
            assertTrue(account.getMoney(AccMoney_PositionProfit)==0);
            assertTrue(account.getMoney(AccMoney_FrozenMargin)==0);
            assertTrue(account.getMoney(AccMoney_FrozenCommission)==0);
        }
    }
}


class TradeServiceTest implements TradeService{

    OrderRefGenImpl orderRefGen;
    Map<String, TxnSessionFactory> txnSessionFactories = new TreeMap<>();

    TradeServiceTest(BeansContainer beansContainer){
        orderRefGen = new OrderRefGenImpl(beansContainer);
        txnSessionFactories.put(TxnSession.PROVIDER_SIM, new SimTxnSessionFactory());
    }

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

}