package trader.service.md;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

import org.junit.Test;

import net.jctp.CThostFtdcDepthMarketDataField;
import trader.common.exception.AppException;
import trader.common.exchangeable.Exchange;
import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.Security;
import trader.service.ServiceErrorCodes;
import trader.service.md.web.WebMarketData;
import trader.service.md.web.WebMarketDataProducer;

public class MarketDataServiceTest implements ServiceErrorCodes {

    @Test
    public void testPrimaryContracts() {
        List<Exchangeable> primaryInstruments = new ArrayList<>();
        List<Exchangeable> primaryInstruments2 = new ArrayList<>();
        boolean result = MarketDataServiceImpl.queryFuturePrimaryInstruments(primaryInstruments, primaryInstruments2);
        assertTrue(result);
        assertTrue(primaryInstruments.size()>=50);
        assertTrue(primaryInstruments2.size()+6 >= 3*primaryInstruments.size() );

        System.out.println(primaryInstruments.size()+" : "+primaryInstruments);
        System.out.println(primaryInstruments2.size()+" : "+primaryInstruments2);
    }

    @Test
    public void test() {
        AppException ap = new AppException(ERR_MD_PRODUCER_DISCONNECTED, "Producer test is disconnected.");
        System.out.println(ap.getMessage());
    }

    @Test
    public void testSinaDataParsing() {
        String str1 = "var hq_str_sh601398=\"工商银行,5.660,5.680,5.660,5.680,5.650,5.650,5.660,99667109,564217383.000,12196100,5.650,6381400,5.640,4424000,5.630,1940300,5.620,1157200,5.610,3847372,5.660,8377056,5.670,12320154,5.680,7226049,5.690,5151500,5.700,2019-07-05,14:19:34,00\";";
        String str2 = "var hq_str_sz000002=\"万 科Ａ,29.450,29.550,29.540,29.680,29.130,29.500,29.540,29796296,875728143.350,109700,29.500,8900,29.490,6400,29.480,300,29.470,4000,29.460,800,29.540,20312,29.550,29898,29.560,12300,29.570,18600,29.580,2019-07-05,14:20:57,00\";";

        CThostFtdcDepthMarketDataField field1 = WebMarketDataProducer.line2field(str1);

        WebMarketData tick = new WebMarketData("p1", new Security(Exchange.SSE, "601398"), field1);
        assertTrue(field1!=null);
        System.out.println(field1);

        CThostFtdcDepthMarketDataField field2 = WebMarketDataProducer.line2field(str2);
        assertTrue(field2!=null);
        System.out.println(field2);

        Matcher matcher = WebMarketDataProducer.SINA_PATTERN.matcher(str1);
        assertTrue(matcher.matches());
        System.out.println("InstrumentId="+matcher.group("InstrumentId"));
        System.out.println("InstrumentName="+matcher.group("InstrumentName"));
        System.out.println("OpenPrice="+matcher.group("OpenPrice"));
        System.out.println("PreClosePrice="+matcher.group("PreClosePrice"));
        System.out.println("LastPrice="+matcher.group("LastPrice"));
        System.out.println("HighestPrice="+matcher.group("HighestPrice"));
        System.out.println("LowestPrice="+matcher.group("LowestPrice"));
        System.out.println("AskPrice="+matcher.group("AskPrice"));
        System.out.println("AskPrice="+matcher.group("AskPrice"));
        System.out.println("Volume="+matcher.group("Volume"));
        System.out.println("Turnover="+matcher.group("Turnover"));

        System.out.println("AskVolume1="+matcher.group("AskVolume1"));
        System.out.println("AskPrice1="+matcher.group("AskPrice1"));
        System.out.println("AskVolume2="+matcher.group("AskVolume2"));
        System.out.println("AskPrice2="+matcher.group("AskPrice2"));
        System.out.println("AskVolume3="+matcher.group("AskVolume3"));
        System.out.println("AskPrice3="+matcher.group("AskPrice3"));
        System.out.println("AskVolume4="+matcher.group("AskVolume4"));
        System.out.println("AskPrice4="+matcher.group("AskPrice4"));
        System.out.println("AskVolume5="+matcher.group("AskVolume5"));
        System.out.println("AskPrice5="+matcher.group("AskPrice5"));

        System.out.println("AskVolume1="+matcher.group("AskVolume1"));
        System.out.println("AskPrice1="+matcher.group("AskPrice1"));
        System.out.println("AskVolume2="+matcher.group("AskVolume2"));
        System.out.println("AskPrice2="+matcher.group("AskPrice2"));
        System.out.println("AskVolume3="+matcher.group("AskVolume3"));
        System.out.println("AskPrice3="+matcher.group("AskPrice3"));
        System.out.println("AskVolume4="+matcher.group("AskVolume4"));
        System.out.println("AskPrice4="+matcher.group("AskPrice4"));
        System.out.println("AskVolume5="+matcher.group("AskVolume5"));
        System.out.println("AskPrice5="+matcher.group("AskPrice5"));

        System.out.println();

        matcher = WebMarketDataProducer.SINA_PATTERN.matcher(str2);
        assertTrue(matcher.matches());
        System.out.println("InstrumetnId="+matcher.group("InstrumentId"));
        System.out.println("InstrumentName="+matcher.group("InstrumentName"));
        System.out.println("OpenPrice="+matcher.group("OpenPrice"));
        System.out.println("PreClosePrice="+matcher.group("PreClosePrice"));
        System.out.println("LastPrice="+matcher.group("LastPrice"));
        System.out.println("HighestPrice="+matcher.group("HighestPrice"));
        System.out.println("LowestPrice="+matcher.group("LowestPrice"));
        System.out.println("AskPrice="+matcher.group("AskPrice"));
        System.out.println("AskPrice="+matcher.group("AskPrice"));
        System.out.println("Volume="+matcher.group("Volume"));
        System.out.println("Turnover="+matcher.group("Turnover"));

        System.out.println("AskVolume1="+matcher.group("AskVolume1"));
        System.out.println("AskPrice1="+matcher.group("AskPrice1"));
        System.out.println("AskVolume2="+matcher.group("AskVolume2"));
        System.out.println("AskPrice2="+matcher.group("AskPrice2"));
        System.out.println("AskVolume3="+matcher.group("AskVolume3"));
        System.out.println("AskPrice3="+matcher.group("AskPrice3"));
        System.out.println("AskVolume4="+matcher.group("AskVolume4"));
        System.out.println("AskPrice4="+matcher.group("AskPrice4"));
        System.out.println("AskVolume5="+matcher.group("AskVolume5"));
        System.out.println("AskPrice5="+matcher.group("AskPrice5"));

        System.out.println("AskVolume1="+matcher.group("AskVolume1"));
        System.out.println("AskPrice1="+matcher.group("AskPrice1"));
        System.out.println("AskVolume2="+matcher.group("AskVolume2"));
        System.out.println("AskPrice2="+matcher.group("AskPrice2"));
        System.out.println("AskVolume3="+matcher.group("AskVolume3"));
        System.out.println("AskPrice3="+matcher.group("AskPrice3"));
        System.out.println("AskVolume4="+matcher.group("AskVolume4"));
        System.out.println("AskPrice4="+matcher.group("AskPrice4"));
        System.out.println("AskVolume5="+matcher.group("AskVolume5"));
        System.out.println("AskPrice5="+matcher.group("AskPrice5"));
    }
}
