package trader.service.md;

import static org.junit.Assert.assertTrue;

import java.util.Collection;

import org.junit.Test;

import trader.common.exception.AppException;
import trader.common.exchangeable.Future;
import trader.service.ServiceErrorCodes;

public class MarketDataServiceTest implements ServiceErrorCodes {

    @Test
    public void testPrimaryContracts() {
        Collection<Future> futures = MarketDataServiceImpl.queryPrimaryContracts();
        assertTrue(futures.size()>0);
        System.out.println(futures);
    }

    @Test
    public void test() {
        AppException ap = new AppException(ERR_MD_PRODUCER_DISCONNECTED, "Producer test is disconnected.");
        System.out.println(ap.getMessage());

    }
}
