package trader.common;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import trader.common.tick.PriceLevel;
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
    public void testDouble2str() {
        double value = 0.059999999;
        String valueStr = PriceUtil.price2str(value);
        assertTrue(valueStr.equals("0.06"));

        assertTrue(PriceUtil.price2str6(value).equals("0.06"));
        assertTrue(PriceUtil.price2str6(0.123456).equals("0.123456"));

        System.out.println(Math.log(46809.4293));
        System.out.println(Math.log(46809.4294));

    }

    @Test
    public void testRound() {
        double value = 10.7268;
        long lv = PriceUtil.price2long(value);
        assertTrue(lv==107268);
        long lv2 = PriceUtil.round(lv);
        assertTrue(lv2==107300);

        assertTrue(PriceUtil.round(107249)==107200);
        assertTrue(PriceUtil.round(107250)==107300);
    }

    @Test
    public void testPriceLevel() {
        PriceLevel min30 = PriceLevel.valueOf("min30");
        assertTrue(min30.value()==30);

        PriceLevel day = PriceLevel.valueOf("day");
        assertTrue(day.value()<0);

        PriceLevel vol1k = PriceLevel.valueOf("vol1k");
        assertTrue(vol1k.value()==1000);

        PriceLevel vol5k = PriceLevel.valueOf("vol5k");
        assertTrue(vol5k.value()==5000);

        PriceLevel vol1kpvd20 = PriceLevel.valueOf("vol1kpvd20");
        assertTrue(vol1kpvd20.value()==1000);
    }

    @Test
    public void test2str() {
        String str = PriceUtil.long2str(200941, 2);
        assertTrue(str.equals("20.09"));
    }

}
