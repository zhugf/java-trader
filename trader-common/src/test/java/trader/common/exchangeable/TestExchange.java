package trader.common.exchangeable;

import static org.junit.Assert.assertTrue;

import java.time.LocalDateTime;

import org.junit.Test;

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
