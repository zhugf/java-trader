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
        TradingMarketInfo marketInfo = zn1609.detectTradingMarketInfo(ldt);
        assertTrue(marketInfo!=null);
        assertTrue(marketInfo.getStage()==MarketTimeStage.MarketBreak);
    }

    @Test
    public void testSHFE(){
        Exchangeable zn1703 = Exchangeable.fromString("zn1703");
        Exchangeable zn1703_2 = Exchangeable.fromString("zn1703");
        assertTrue(zn1703.uniqueIntId()==zn1703_2.uniqueIntId());
        assertTrue(zn1703.exchange() == Exchange.SHFE);

        LocalDateTime ldt = LocalDateTime.of(2017, 3, 3, 9, 0);
        assertTrue( Exchange.SHFE.detectMarketTypeAt(zn1703, ldt) == Exchange.MarketType.Day );

        ldt = LocalDateTime.of(2017, 3, 3, 21, 0);
        assertTrue( Exchange.SHFE.detectMarketTypeAt(zn1703, ldt) == Exchange.MarketType.Night );
    }

}
