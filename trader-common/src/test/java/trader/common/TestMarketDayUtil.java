package trader.common;

import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.Test;

import trader.common.exchangeable.Exchange;
import trader.common.exchangeable.MarketDayUtil;

public class TestMarketDayUtil {

	@Test
	public void testTradingDay() {
	    LocalDate tradingDay = MarketDayUtil.getTradingDay(Exchange.SHFE, LocalDateTime.of(2018, 11, 27, 23, 00, 0));
	    assertTrue(tradingDay.equals(LocalDate.of(2018, 11, 28)));

	    tradingDay = MarketDayUtil.getTradingDay(Exchange.SHFE, LocalDateTime.of(2018, 11, 27, 9, 00, 0));
	    assertTrue(tradingDay.equals(LocalDate.of(2018, 11, 27)));

        tradingDay = MarketDayUtil.getTradingDay(Exchange.SHFE, LocalDateTime.of(2018, 12, 28, 9, 00, 0));
        assertTrue(tradingDay.equals(LocalDate.of(2018, 12, 28)));

        tradingDay = MarketDayUtil.getTradingDay(Exchange.SHFE, LocalDateTime.of(2018, 12, 28, 20, 00, 0));
        assertTrue(tradingDay==null);
	}

}
