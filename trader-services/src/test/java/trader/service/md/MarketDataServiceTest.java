package trader.service.md;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import trader.common.exception.AppException;
import trader.common.exchangeable.Exchangeable;
import trader.service.ServiceErrorCodes;

public class MarketDataServiceTest implements ServiceErrorCodes {

    @Test
    public void testPrimaryContracts() {
        List<Exchangeable> primaryInstruments = new ArrayList<>();
        List<Exchangeable> primaryInstruments2 = new ArrayList<>();
        boolean result = MarketDataServiceImpl.queryFuturePrimaryInstruments(primaryInstruments, primaryInstruments2);
        assertTrue(result);
        assertTrue(primaryInstruments.size()>=50);
        assertTrue(primaryInstruments2.size()+6 >= 3*primaryInstruments.size() );

        System.out.println(primaryInstruments.size()+" : "+primaryInstruments);
        System.out.println(primaryInstruments2.size()+" : "+primaryInstruments2);
    }

    @Test
    public void test() {
        AppException ap = new AppException(ERR_MD_PRODUCER_DISCONNECTED, "Producer test is disconnected.");
        System.out.println(ap.getMessage());
    }
}
