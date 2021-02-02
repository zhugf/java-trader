package trader.common;

import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.junit.Test;

import trader.common.exchangeable.Exchange;
import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableType;
import trader.common.exchangeable.Future;
import trader.common.exchangeable.Option;
import trader.common.exchangeable.OptionType;
import trader.common.util.DateUtil;
import trader.common.util.StringUtil;

public class TestFuture {

    @Test
    public void test_cffex_T() {
        List<Future> result = Future.instrumentsFromMarketDay(DateUtil.str2localdate("20200713"), "T");
        assertTrue(result.get(0).id().equals("T2009"));
        assertTrue(result.get(1).id().equals("T2012"));
        assertTrue(result.get(2).id().equals("T2103"));
    }

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

        result = Future.instrumentsFromMarketDay(DateUtil.str2localdate("20210112"), "lh");
        assertTrue(result.size()>0);
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

    @Test
    public void testFutureCombo() {
        Exchangeable e = Exchangeable.fromString("SP a2009&a2009");
        assertTrue(e.exchange()==Exchange.DCE);
        assertTrue(e.getType()==ExchangeableType.FUTURE_COMBO);

        e = Exchangeable.fromString("SPD ZC012&ZC102");
        assertTrue(e.exchange()==Exchange.CZCE);
        assertTrue(e.getType()==ExchangeableType.FUTURE_COMBO);
    }

    @Test
    public void test_czce() {
        Exchangeable SR010 = Exchangeable.fromString("SR010");
        Exchangeable SR909 = Exchangeable.fromString("SR909");
        assertTrue( ((Future)SR010).getCanonicalDeliveryDate().equals("2010"));
        assertTrue( ((Future)SR909).getCanonicalDeliveryDate().equals("1909"));

        assertTrue(SR909.compareTo(SR010)<0);
        Exchangeable PK112 = Exchangeable.fromString("PK112");
        assertTrue(PK112.exchange()==Exchange.CZCE);
    }


    public static final Pattern OPTION_PATTERN = Pattern.compile("([a-zA-Z]+)(\\d{3,4})(-?([P|C])-?)(\\d{2,})");

    @Test
    public void testFutureOption() {
        assertTrue( OPTION_PATTERN.matcher("m2012-C-3100").matches());
        assertTrue( OPTION_PATTERN.matcher("ru2103C11250").matches());

        Exchangeable e = Exchangeable.fromString("m2012-C-3100");
        assertTrue(e.getType()==ExchangeableType.OPTION);
        Option opt = (Option)e;
        assertTrue(opt.getOptionType()==OptionType.Call);

        e = Exchangeable.fromString("DCE", "m2107-P-3050");
        assertTrue(e.getType()==ExchangeableType.OPTION);
        opt = (Option)e;
        assertTrue(opt.getOptionType()==OptionType.Put);

        e = Exchangeable.fromString("SHFE", "ru2103C11250");
        assertTrue(e.getType()==ExchangeableType.OPTION);
        opt = (Option)e;
        assertTrue(opt.getOptionType()==OptionType.Call);
    }

    /**
     * 测试对期货合约进行排序
     */
    @Test
    public void testFutureSort() {
        String instruments = "MA909,MA101,MA103,MA105";
        TreeSet<Exchangeable> futures = new TreeSet<>();
        for(String i:StringUtil.split(instruments, ",")) {
            futures.add(Future.fromString(i));
        }
        List<Exchangeable> list = new ArrayList<>(futures);
        assertTrue(list.get(0).equals(Future.fromString("MA909")));
    }
}
