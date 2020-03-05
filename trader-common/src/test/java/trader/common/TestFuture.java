package trader.common;

import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.junit.Test;

import trader.common.exchangeable.Exchange;
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

        result = Future.instrumentsFromMarketDay(DateUtil.str2localdate("20190531"), "m");
        assertTrue(result.size()==8);
        assertTrue(result.get(0).id().equals("m1907"));
        assertTrue(result.get(1).id().equals("m1908"));
        assertTrue(result.get(2).id().equals("m1909"));
        assertTrue(result.get(3).id().equals("m1911"));
        assertTrue(result.get(4).id().equals("m1912"));
        assertTrue(result.get(5).id().equals("m2001"));
        assertTrue(result.get(6).id().equals("m2003"));
        assertTrue(result.get(7).id().equals("m2005"));
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

        Exchangeable e = Exchangeable.fromString("RR2001");
        assertTrue(e.getType()==ExchangeableType.FUTURE && e.exchange()==Exchange.DCE);
    }

    @Test
    public void testINE() {
        Exchangeable e = Exchangeable.fromString("INE.sc1908");
        assertTrue(e.getType()==ExchangeableType.FUTURE);
    }



    @Test
    public void test_shfe() {
        Exchangeable e = Exchangeable.fromString("AU1906");
        assertTrue(e.getType()==ExchangeableType.FUTURE);

        Exchangeable e2 = Exchangeable.fromString("au1906");
        assertTrue(e2.getType()==ExchangeableType.FUTURE);

        Exchangeable nr2002 = Exchangeable.fromString("nr2002");
        assertTrue(nr2002.getType()==ExchangeableType.FUTURE);
    }

    @Test
    public void test_shfe_au() {
        LocalDate marketDay = DateUtil.str2localdate("20200305");
        List<Future> futures = Future.instrumentsFromMarketDay(marketDay, "au");
        assert(futures.toString().indexOf("au2006")>=0);
    }
}
