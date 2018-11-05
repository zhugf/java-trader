package trader.common;

import static org.junit.Assert.assertTrue;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.junit.Test;

import trader.common.exchangeable.Exchange;
import trader.common.util.DateUtil;

public class TestDateUtil {

    @Test
    public void testZoneOffset(){
        LocalDateTime dt = LocalDateTime.now();

        ZoneId zoneId = Exchange.SSE.getZoneId();
        ZonedDateTime zdt = dt.atZone(zoneId);
        ZoneOffset ofs = zdt.getOffset();
    }

    @Test
    public void testRount() {
        { //09:00:00.500 -> 09:00:00
            LocalDateTime ldt = LocalDateTime.of(2018, 10, 25, 9, 0, 0).plusNanos(500*1000000);
            LocalDateTime ldt2 = DateUtil.round(ldt);
            assertTrue(ldt.isAfter(ldt2));
        }
        { //09:00:59.500 -> 09:01:00
            LocalDateTime ldt = LocalDateTime.of(2018, 10, 25, 9, 0, 59).plusNanos(500*1000000);
            LocalDateTime ldt2 = DateUtil.round(ldt);
            assertTrue(ldt.isBefore(ldt2));
        }
    }

    @Test
    public void testTime2int() {
        assertTrue(DateUtil.time2int("09:00:00")==90000);
        assertTrue(DateUtil.time2int("12:00:00")==120000);
        assertTrue(DateUtil.time2int("9:12:34")==91234);
        assertTrue(DateUtil.time2int("23:45:21")==234521);
    }

}
