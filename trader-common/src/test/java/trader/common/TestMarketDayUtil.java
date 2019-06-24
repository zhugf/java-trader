package trader.common;

import static org.junit.Assert.assertTrue;

import java.time.LocalDate;

import org.junit.Test;

import trader.common.exchangeable.Exchange;
import trader.common.exchangeable.MarketDayUtil;
import trader.common.util.DateUtil;

public class TestMarketDayUtil {

	@Test
	public void testTradingDay() {
	    LocalDate d20190430 = DateUtil.str2localdate("20190430");
	    LocalDate next = MarketDayUtil.nextMarketDay(Exchange.SHFE, d20190430);
	    assertTrue(DateUtil.date2str(next).equals("20190506"));
	}

}
