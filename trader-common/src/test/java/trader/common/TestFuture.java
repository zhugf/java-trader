package trader.common;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.Future;
import trader.common.util.DateUtil;
import trader.common.util.StringUtil;

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
    public void test1901() {
        String str = "AP901,CF901,CY810,FG901,IC1809,IF1809,IH1809,LR903,MA901,OI901,RM901,SF901,SM901,SR901,T1812,TA901,TF1812,WH901,ZC901,a1901,ag1812,al1811,au1812,b1901,b1905,bu1812,c1901,c1905,cs1901,cu1810,cu1811,fu1901,hc1901,i1901,j1901,jd1901,jm1901,l1901,m1901,ni1811,p1901,pb1810,pb1811,pp1901,rb1901,ru1901,sc1812,sn1901,v1901,y1901,zn1811";
        String[] instrumentIds = StringUtil.split(str, ",|;|\r|\n");
        List<Exchangeable> r = new ArrayList<>();
        for(String instrumentId:instrumentIds) {
            Exchangeable e = Exchangeable.fromString(instrumentId);
            r.add(e);
        }
    }

}
