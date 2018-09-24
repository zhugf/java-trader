package trader.common;

import java.time.*;

import org.junit.Test;

import trader.common.exchangeable.Exchange;

public class TestDateUtil {

    @Test
    public void testZoneOffset(){
        LocalDateTime dt = LocalDateTime.now();

        ZoneId zoneId = Exchange.SSE.getZoneId();
        ZonedDateTime zdt = dt.atZone(zoneId);
        ZoneOffset ofs = zdt.getOffset();

    }
}
