package trader.common;

import java.time.LocalDate;

import org.junit.Test;

import trader.common.exchangeable.MarketDayUtil;

public class TestMarketDayUtil {

	@Test
	public void testLastMarketDay(){
		LocalDate ddd = MarketDayUtil.lastMarketDay(null, false);
	}
}
