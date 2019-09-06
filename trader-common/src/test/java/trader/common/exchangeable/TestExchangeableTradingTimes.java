package trader.common.exchangeable;

import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.Test;

import trader.common.util.DateUtil;
import trader.common.util.PriceUtil;
import trader.common.util.StringUtil;

public class TestExchangeableTradingTimes {

    @Test
    public void test_ru() {
        Exchangeable ru1901 = Exchangeable.fromString("ru1901");
        assertTrue(ru1901.exchange()==Exchange.SHFE);
        ExchangeableTradingTimes tradingTimes = ru1901.exchange().getTradingTimes(ru1901, DateUtil.str2localdate("20181203"));
        assertTrue(tradingTimes!=null);

        LocalDateTime time0 = DateUtil.str2localdatetime("20181130 21:01:01");
        assertTrue(tradingTimes.getTimeStage(time0)==MarketTimeStage.MarketOpen);
        assertTrue(tradingTimes.getSegmentType(time0)==MarketType.Night);
        assertTrue(tradingTimes.getTradingTimeInSegment(time0, MarketType.Night)==61*1000);

        LocalDateTime time1 = DateUtil.str2localdatetime("20181203 14:01:01");
        assertTrue(tradingTimes.getSegmentType(time1)==MarketType.Day);

        LocalDateTime time2 = DateUtil.str2localdatetime("20181203 15:01:01");
        assertTrue(tradingTimes.getSegmentType(time2)==null );

        LocalDateTime time4 = DateUtil.str2localdatetime("20181203 09:01:01");
        assertTrue(tradingTimes.getTradingTimeInSegment(time4, MarketType.Day)==61*1000);

        LocalDateTime time5 = DateUtil.str2localdatetime("20181130 22:59:00");
        assertTrue(tradingTimes.getSegmentType(time5)==MarketType.Night);
        assertTrue(tradingTimes.getTotalTradingMillisInSegment(MarketType.Night)-tradingTimes.getTradingTimeInSegment(time5, MarketType.Night)==60*1000);

        LocalDateTime time6 = DateUtil.str2localdatetime("20181203 14:59:00");
        assertTrue(tradingTimes.getSegmentType(time6)==MarketType.Day);
        assertTrue(tradingTimes.getTotalTradingMillisInSegment(MarketType.Day)-tradingTimes.getTradingTimeInSegment(time6, MarketType.Day)==60*1000);

        {
            LocalDateTime time = DateUtil.str2localdatetime("20181130 20:45:00");
            assertTrue(tradingTimes.getSegmentType(time)==MarketType.Night);
        }
        {
            LocalDateTime time = DateUtil.str2localdatetime("20181203 08:45:00");
            assertTrue(tradingTimes.getSegmentType(time)==MarketType.Day);
        }
    }

    @Test
    public void testTime() {
        LocalDateTime time = DateUtil.str2localdatetime("2019-01-16 11:30:01");
        Exchangeable j1905 = Exchangeable.fromString("j1905");
        ExchangeableTradingTimes tradingTimes = j1905.exchange().getTradingTimes(j1905, time.toLocalDate());

        LocalDateTime time2 = DateUtil.str2localdatetime("2019-01-16 11:30:00");
        int tradingTime2 = tradingTimes.getTradingTime(time2);
        int tradingTime = tradingTimes.getTradingTime(time);
        int totalMillis = tradingTimes.getTotalTradingMillis();
        assertTrue(tradingTime==tradingTime2);
    }

    @Test
    public void testSC1809() {
        Exchangeable sc1809 = Exchangeable.fromString("sc1809");
        LocalDate tradingDay = DateUtil.str2localdate("20180326");
        assertTrue(sc1809.exchange().getTradingTimes(sc1809, tradingDay)!=null);
    }

    @Test
    public void test_au() {
        LocalDateTime dt = DateUtil.str2localdatetime("20190713 00:23:24");
        ExchangeableTradingTimes tradingTimes = Exchange.SHFE.detectTradingTimes("au", dt);
        assertTrue(tradingTimes!=null);
    }

    @Test
    public void testCommodity() {
        Exchangeable
        exx = Exchangeable.fromString("i.dce");
        assertTrue(exx.exchange()==Exchange.DCE);
        assertTrue(StringUtil.equals("i", exx.commodity()) );
        assertTrue(exx.uniqueId().equals("i.dce"));

        exx = Exchangeable.fromString("SR.czce");
        assertTrue(exx.exchange()==Exchange.CZCE);
        assertTrue(StringUtil.equals("SR", exx.commodity()) );

        assertTrue(exx.commodity().equals("SR"));
        assertTrue( PriceUtil.config2long("5", exx.getPriceTick()) == 50000 );
        assertTrue( PriceUtil.config2long("6t", exx.getPriceTick()) == 60000 );

    }

}
