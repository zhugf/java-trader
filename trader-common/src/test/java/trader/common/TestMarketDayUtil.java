package trader.common;

import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.Test;

import trader.common.exchangeable.Exchange;
import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableTradingTimes;
import trader.common.exchangeable.MarketDayUtil;
import trader.common.util.DateUtil;

public class TestMarketDayUtil {

	@Test
	public void testTradingDay() {
	    {
	        LocalDate date = DateUtil.str2localdate("20191206");
	        Exchangeable sa005 = Exchangeable.fromString("SA005");
	        ExchangeableTradingTimes tradingTimes = sa005.exchange().getTradingTimes(sa005, date);
	        assertTrue( DateUtil.date2str(tradingTimes.getTradingDay()).equals("20191206") );
	    }

	    LocalDate d20190430 = DateUtil.str2localdate("20190430");
	    LocalDate next = MarketDayUtil.nextMarketDay(Exchange.SHFE, d20190430);
	    assertTrue(DateUtil.date2str(next).equals("20190506"));

	    LocalDateTime ts = DateUtil.str2localdatetime("20190916 08:50:00");
	    ExchangeableTradingTimes tradingTimes = Exchange.SHFE.detectTradingTimes("au", ts);
	    assertTrue( DateUtil.date2str(tradingTimes.getTradingDay()).equals("20190916") );

	}

}
