package trader.common.exchangeable;

import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

import trader.common.util.DateUtil;

public class ExchangeContractTest {

    @Test
    public void test_shfe_au() {
        List<Future> futures = Future.instrumentsFromMarketDay(DateUtil.str2localdate("20190926"), "au");
        assertTrue(futures.get(0).equals(Exchangeable.fromString("au1910")));
    }

}
