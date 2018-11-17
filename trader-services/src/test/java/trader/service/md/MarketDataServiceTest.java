package trader.service.md;

import static org.junit.Assert.assertTrue;

import java.util.Collection;

import org.junit.Test;

import trader.common.exchangeable.Future;

public class MarketDataServiceTest {

    @Test
    public void testPrimaryContracts() {
        Collection<Future> futures = MarketDataServiceImpl.queryPrimaryContracts();
        assertTrue(futures.size()>0);
        System.out.println(futures);
    }
}
