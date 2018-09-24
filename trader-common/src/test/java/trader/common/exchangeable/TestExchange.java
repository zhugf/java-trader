package trader.common.exchangeable;

import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.junit.Test;

public class TestExchange {

    @Test
    public void testMarketTime()
    {
        Exchangeable sse_000300 = Exchangeable.fromString("sse.000300");
        LocalDate tradingDay = LocalDate.of(2016, 1, 1);
        assertTrue( sse_000300.getTradingMilliSeconds(tradingDay, LocalTime.of(9, 00, 00))<=0 );
        assertTrue( sse_000300.getTradingMilliSeconds(tradingDay, LocalTime.of(9, 30, 00))==0 );
        assertTrue( sse_000300.getTradingMilliSeconds(tradingDay, LocalTime.of(10, 30, 00))==(60*60)*1000 );
        assertTrue( sse_000300.getTradingMilliSeconds(tradingDay, LocalTime.of(11, 30, 00))==(60*60)*2*1000 );
        assertTrue( sse_000300.getTradingMilliSeconds(tradingDay, LocalTime.of(12, 30, 00))==(60*60)*2*1000 );
        assertTrue( sse_000300.getTradingMilliSeconds(tradingDay, LocalTime.of(14, 00, 00))==(60*60)*3*1000 );
        assertTrue( sse_000300.getTradingMilliSeconds(tradingDay, LocalTime.of(15, 00, 00))==(60*60)*4*1000 );
        assertTrue( sse_000300.getTradingMilliSeconds(tradingDay, LocalTime.of(16, 00, 00))==(60*60)*4*1000 );
    }

    /**
     * TODO 检查shfe zn的10:15是否存在中场暂停
     */
    @Test
    public void testMarketTime3()
    {
        Exchangeable zn1609 = Exchangeable.fromString("zn1609");
        LocalDateTime ldt = LocalDateTime.of(2016, 9, 2, 10, 15, 00, 500*1000*1000);
        MarketTimeStage mts = zn1609.getTimeFrame(ldt);
        //assertTrue(mts==MarketTimeStage.MarketBreak);
    }

    @Test
    public void testIF(){
        Exchangeable if1610 = Exchangeable.fromString("cffex.IF1610");
        LocalDate tradingDay = LocalDate.of(2016, 1, 1);

        LocalDateTime marketOpenCloseTime[] = if1610.getOpenCloseTime(tradingDay);
        assertTrue( marketOpenCloseTime[0].toLocalTime().equals(LocalTime.of(9, 30)));
        assertTrue( marketOpenCloseTime[1].toLocalTime().equals(LocalTime.of(15, 00)));

    }

    @Test
    public void testSHFE(){
        Exchangeable zn1703 = Exchangeable.fromString("zn1703");
        assertTrue(zn1703.exchange() == Exchange.SHFE);

        LocalDateTime ldt = LocalDateTime.of(2017, 3, 3, 9, 0);
        assertTrue( Exchange.SHFE.detectMarketTypeAt(zn1703, ldt) == Exchange.MarketType.Day );

        ldt = LocalDateTime.of(2017, 3, 3, 21, 0);
        assertTrue( Exchange.SHFE.detectMarketTypeAt(zn1703, ldt) == Exchange.MarketType.Night );
    }

}
