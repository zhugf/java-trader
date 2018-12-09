package trader.common;

import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;

import org.junit.Test;

import trader.common.util.PriceUtil;

public class TestPriceUtil {

    @Test
    public void test1() {
        long p = PriceUtil.price2long(0.7000000006);
        assertTrue(p==7000);
        p = PriceUtil.price2long(0.699999999999);
        assertTrue(p==7000);
        double d = PriceUtil.long2price(7000);
        assertTrue( d==0.70);
        assertTrue( "70.69".equals( PriceUtil.price2str(70.69)));
        assertTrue( "70.00".equals( PriceUtil.price2str(70)));
        assertTrue( "70.02".equals( PriceUtil.price2str(70.02)));
        assertTrue( "-70.02".equals( PriceUtil.price2str(-70.02)));
        assertTrue( "-70.52".equals( PriceUtil.price2str(-70.52)));
    }

    @Test
    public void test3()
    {
        assertTrue( "4.103".equals( PriceUtil.price2str(4.103)));

        double d = 1.7976931348623157E308;
        if ( Double.MAX_VALUE != d) {
            assertTrue(false);
        }
    }


    @Test
    public void test4(){
        assertTrue( PriceUtil.price2long(Double.MAX_VALUE) == Long.MAX_VALUE);
        assertTrue( PriceUtil.price2str(Double.MAX_VALUE).equals(PriceUtil.MAX_STR));
        assertTrue( PriceUtil.long2str(Long.MAX_VALUE).equals(PriceUtil.MAX_STR));
    }

    @Test
    public void test5(){
        long v = 31242039;
        assertTrue( PriceUtil.long2str(v, 2).equals("3124.20"));
    }

    @Test
    public void testPercent(){
        long percent = PriceUtil.percent2long(10, 100);
        assertTrue(percent==1000);
        String percentStr = PriceUtil.percent2str(percent);
        assertTrue("10.00".equals(percentStr));
    }

    @Test
    public void testPercent2double() {
        String str = "6.000000000000001E-8";
        double value = Double.valueOf(str);
        System.out.println(new BigDecimal(value));
    }

    @Test
    public void testDouble2str() {
        double value = 0.059999999;
        String valueStr = PriceUtil.price2str(value);
        assertTrue(valueStr.equals("0.06"));
    }

}
