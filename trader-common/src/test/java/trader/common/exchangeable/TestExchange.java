package trader.common.exchangeable;

import static org.junit.Assert.assertTrue;

import java.time.LocalDateTime;

import org.junit.Test;

import trader.common.util.DateUtil;

public class TestExchange {

    /**
     * 检查shfe zn的10:15是否存在中场暂停
     */
    @Test
    public void testMarketTime3()
    {
        Exchangeable zn1609 = Exchangeable.fromString("zn1609");
        LocalDateTime ldt = LocalDateTime.of(2016, 9, 2, 10, 15, 01, 500*1000*1000);
        ExchangeableTradingTimes tradingTimes = zn1609.exchange().detectTradingTimes(zn1609, ldt);
        assertTrue(tradingTimes!=null);
        assertTrue(tradingTimes.getTimeStage(ldt) == MarketTimeStage.MarketBreak);
    }

    /**
     * 交易时间比较
     */
    @Test
    public void testTradingTimeCompare() {
        Exchangeable zn1609 = Exchangeable.fromString("zn1609");
        assertTrue(zn1609.exchange().tradingTimeCompare(zn1609, 0, 0)==0);

        long t1 = DateUtil.localdatetime2long(LocalDateTime.of(2023, 1, 9, 14, 50, 00, 0));
        long t2 = DateUtil.localdatetime2long(LocalDateTime.of(2023, 1, 9, 21, 10, 00, 0));
        assertTrue(zn1609.exchange.tradingTimeCompare(zn1609, t1, t2)==20*60*1000);
    }

    @Test
    public void testSHFE(){
        Exchangeable zn1703 = Exchangeable.fromString("zn1703");
        Exchangeable zn1703_2 = Exchangeable.fromString("zn1703");
        assertTrue(zn1703.uniqueIntId()==zn1703_2.uniqueIntId());
        assertTrue(zn1703.exchange() == Exchange.SHFE);

        LocalDateTime ldt = LocalDateTime.of(2018, 12, 28, 14, 35, 01);
        ExchangeableTradingTimes tradingTimes = Exchange.SHFE.detectTradingTimes("au", ldt);
        assertTrue(tradingTimes!=null && tradingTimes.getInstrument().exchange()==Exchange.SHFE);
    }

    @Test
    public void testZCE() {
        Exchangeable rm907 = Exchangeable.fromString("RM907");
        assertTrue(rm907.exchange()==Exchange.CZCE);

        Exchangeable pk112 = Exchangeable.fromString("PK112");
        assertTrue(pk112.exchange()==Exchange.CZCE);
    }

    @Test
    public void testdce() {
        Exchangeable m1909 = Exchangeable.fromString("m1909.dce");
        assertTrue(m1909.exchange().isFuture());
        assertTrue(m1909.getType()==ExchangeableType.FUTURE);
        assertTrue(m1909.getVolumeMutiplier()!=1);
    }

    @Test
    public void testGFEX() {
        Exchangeable si2401 = Exchangeable.fromString("si2404");
        assertTrue(si2401.exchange().isFuture());
        Exchangeable lc2404 = Exchangeable.fromString("lc2404");
        assertTrue(lc2404.exchange().isFuture());
        assertTrue(lc2404.exchange()==Exchange.GFEX);
    }

    @Test
    public void testTradingMarketInfo() {
        Exchangeable au1906 = Exchangeable.fromString("au1906");

        LocalDateTime ldt = LocalDateTime.of(2018, 12, 28, 14, 35, 01);
        long t = System.currentTimeMillis();
        for(int i=0;i<100000;i++) {
            ExchangeableTradingTimes marketInfo = au1906.exchange().detectTradingTimes(au1906, ldt);
        }
        System.out.println("detectTradingMarketInfo "+(System.currentTimeMillis()-t)+" ms");

        t = System.currentTimeMillis();
    }


}
