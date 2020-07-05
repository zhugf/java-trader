package trader.common.util;

import static org.junit.Assert.assertTrue;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.junit.Test;

public class TestUUIDUtil {

    @Test
    public void test() {
        List<UUID> uuids = new LinkedList<>();
        for(int i=0;i<10000; i++) {
            uuids.add(UUIDUtil.genUUID());
        }
        assertTrue(uuids.size()==10000);
    }

}
