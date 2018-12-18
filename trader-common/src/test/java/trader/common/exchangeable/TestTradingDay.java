package trader.common.exchangeable;

import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.Test;

public class TestTradingDay {

    @Test
    public void test_security() {
        Exchangeable hs300 = Security.HS300;
        LocalDateTime dt = LocalDateTime.of(2015, 12, 31, 9, 30, 0);
        LocalDate tradingDay = hs300.detectTradingDay(dt);
        assertTrue(tradingDay.equals(LocalDate.of(2015, 12, 31)));

        dt = LocalDateTime.of(2016, 1, 1, 9, 0, 0);
        tradingDay = hs300.detectTradingDay(dt);
        assertTrue(tradingDay==null);
    }

    @Test
    public void test_if() {
        Exchangeable if1601 = Exchangeable.fromString("cffex.IF1601");
        LocalDateTime dt = LocalDateTime.of(2015, 12, 31, 9, 30, 0);
        LocalDate tradingDay = if1601.detectTradingDay(dt);
        assertTrue(tradingDay.equals(LocalDate.of(2015, 12, 31)));

        dt = LocalDateTime.of(2016, 1, 1, 9, 30, 0);
        tradingDay = if1601.detectTradingDay(dt);
        assertTrue(tradingDay==null);

        dt = LocalDateTime.of(2015, 12, 31, 21, 0, 0);
        tradingDay = if1601.detectTradingDay(dt);
        assertTrue(tradingDay==null);
    }

    @Test
    public void test_RU() {
        Exchangeable RU1601 = Exchangeable.fromString("ru1601");
        LocalDateTime dt = LocalDateTime.of(2015, 12, 31, 9, 0, 0);
        LocalDate tradingDay = RU1601.detectTradingDay(dt);
        assertTrue(tradingDay.equals(LocalDate.of(2015, 12, 31)));

        dt = LocalDateTime.of(2016, 1, 1, 9, 0, 0);
        tradingDay = RU1601.detectTradingDay(dt);
        assertTrue(tradingDay==null);

        //夜盘-下一个交易日
        dt = LocalDateTime.of(2015, 12, 31, 21, 0, 0);
        tradingDay = RU1601.detectTradingDay(dt);
        assertTrue(tradingDay.equals(LocalDate.of(2016, 1, 4)));

        //夜盘-下一个交易日周末
        dt = LocalDateTime.of(2018, 11, 9, 20, 50, 0);
        tradingDay = RU1601.detectTradingDay(dt);
        assertTrue(tradingDay.equals(LocalDate.of(2018, 11, 12)));

    }

    @Test
    public void testTradMillis() {
        Exchangeable RU1901 = Exchangeable.fromString("ru1901");
        LocalDateTime dt = LocalDateTime.of(2018, 10, 25, 9, 0, 0);
        assertTrue( RU1901.detectTradingMarketInfo(dt).getTradingTime()==0);

        LocalDateTime dt2 = LocalDateTime.of(2018, 10, 25, 10, 15, 0);
        assertTrue( RU1901.detectTradingMarketInfo(dt2).getTradingTime()==75*60*1000);

        LocalDateTime dt3 = LocalDateTime.of(2018, 10, 25, 10, 15, 0).plusNanos(500*1000000);
        assertTrue(dt3.isAfter(dt2));
        assertTrue( RU1901.detectTradingMarketInfo(dt3).getTradingTime()==75*60*1000);

        LocalDateTime dt4 = LocalDateTime.of(2018, 10, 25, 13, 30, 0);
        long tradeMillis = RU1901.detectTradingMarketInfo(dt4).getTradingTime();
        assertTrue(tradeMillis>0);
    }
}
