package trader.common;

import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableType;
import trader.common.exchangeable.Future;
import trader.common.util.DateUtil;

public class TestFuture {

    @Test
    public void test() {
        List<Future> result = Future.instrumentsFromMarketDay(DateUtil.str2localdate("20150901"), "IF");
        assertTrue(result.get(0).id().equals("IF1509"));
        assertTrue(result.get(1).id().equals("IF1510"));
        assertTrue(result.get(2).id().equals("IF1512"));
        assertTrue(result.get(3).id().equals("IF1603"));


        result = Future.instrumentsFromMarketDay(DateUtil.str2localdate("20150801"), "IF");
        assertTrue(result.get(0).id().equals("IF1508"));
        assertTrue(result.get(1).id().equals("IF1509"));
        assertTrue(result.get(2).id().equals("IF1512"));
        assertTrue(result.get(3).id().equals("IF1603"));

        result = Future.instrumentsFromMarketDay(DateUtil.str2localdate("20150826"), "IF");
        assertTrue(result.get(0).id().equals("IF1509"));
        assertTrue(result.get(1).id().equals("IF1510"));
        assertTrue(result.get(2).id().equals("IF1512"));
        assertTrue(result.get(3).id().equals("IF1603"));
    }

    @Test
    public void test_dce(){
        List<Future> result = Future.instrumentsFromMarketDay(DateUtil.str2localdate("20161028"), "c");
        assertTrue(result.get(0).id().equals("c1611"));
        assertTrue(result.get(1).id().equals("c1701"));
        assertTrue(result.get(2).id().equals("c1703"));
        assertTrue(result.get(3).id().equals("c1705"));
        assertTrue(result.get(4).id().equals("c1707"));
        assertTrue(result.get(5).id().equals("c1709"));
    }

    @Test
    public void testINE() {
        Exchangeable e = Exchangeable.fromString("INE.sc1908");
        assertTrue(e.getType()==ExchangeableType.FUTURE);
    }

}
