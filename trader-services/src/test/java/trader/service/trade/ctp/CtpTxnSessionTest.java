package trader.service.trade.ctp;

import static org.junit.Assert.assertTrue;

import java.util.regex.Pattern;

import org.junit.Test;

public class CtpTxnSessionTest {

    @Test
    public void testContract() {
        Pattern contractPattern = Pattern.compile("\\w+\\d+");
        assertTrue( contractPattern.matcher("l1908").matches() );
        assertTrue( !contractPattern.matcher("m1812-C-3150").matches() );
    }
}
