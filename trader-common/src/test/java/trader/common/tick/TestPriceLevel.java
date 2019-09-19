package trader.common.tick;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TestPriceLevel {

    @Test
    public void testVolLevel() {
        PriceLevel vol563 = PriceLevel.valueOf("vol563");
        assertTrue(vol563.value()==563);

        PriceLevel volDaily = PriceLevel.valueOf("volDaily");
        assertTrue(volDaily == PriceLevel.VOLDAILY);
        assertTrue(volDaily.value()==-1);

        assertTrue( PriceLevel.DAY.prefix().equals(PriceLevel.LEVEL_DAY) );
    }
}
