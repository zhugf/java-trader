package trader.common.exchangeable;

import static org.junit.Assert.assertTrue;

import java.time.LocalDateTime;

import org.junit.Test;

public class ExchangeContractTest {

    /**
     * 测试日市夜市
     */
    @Test
    public void testRU() {
        Exchangeable ru1901 = Exchangeable.fromString("ru1901");
        {
            LocalDateTime dateTime = LocalDateTime.of(2018, 10, 25, 9, 00, 00);
            TradingMarketInfo marketInfo = ru1901.detectTradingMarketInfo(dateTime);
            assertTrue(marketInfo!=null);
            assertTrue(marketInfo.getStage()==MarketTimeStage.MarketOpen);
        }
        {
            LocalDateTime dateTime = LocalDateTime.of(2018, 10, 25, 21, 00, 00);
            TradingMarketInfo marketInfo = ru1901.detectTradingMarketInfo(dateTime);
            assertTrue(marketInfo!=null);
            assertTrue(marketInfo.getStage()==MarketTimeStage.MarketOpen);
        }
    }

}
