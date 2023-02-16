package trader.service.md;

import static org.junit.Assert.assertTrue;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

import net.jctp.CThostFtdcDepthMarketDataField;
import trader.common.exception.AppException;
import trader.common.exchangeable.Exchange;
import trader.common.exchangeable.Security;
import trader.service.ServiceErrorCodes;
import trader.service.md.web.WebMarketData;
import trader.service.md.web.WebMarketDataProducer;

public class MarketDataServiceTest implements ServiceErrorCodes {

    @Test
    public void test() {
        AppException ap = new AppException(ERR_MD_PRODUCER_DISCONNECTED, "Producer test is disconnected.");
        System.out.println(ap.getMessage());
    }

    @Test
    public void testSinaDataParsing() {

        String str1 = "var hq_str_sh000016=\"上证50,2754.9507,2752.3385,2763.2606,2766.2451,2749.8507,0,0,3196684,6147661839,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2023-02-16,09:41:50,00,\";";
        String str2 = "var hq_str_sh000300=\"沪深300,4125.4087,4123.6893,4129.0071,4132.2958,4117.6013,0,0,18262492,30561513261,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2023-02-16,09:41:50,00,\";";
        String str3 = "var hq_str_sh000905=\"中证500,6384.2145,6384.3479,6380.8826,6388.1951,6379.5681,0,0,18049889,22262037350,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2023-02-16,09:41:50,00,\";";

        Matcher matcher = WebMarketDataProducer.SINA_PATTERN.matcher(str3);
        assertTrue(matcher.find());
        assertTrue(matcher.group("Volume").equals("18049889"));

        CThostFtdcDepthMarketDataField field1 = WebMarketDataProducer.line2field(str1);

        WebMarketData tick = new WebMarketData("p1", new Security(Exchange.SSE, "601398"), field1);
        assertTrue(field1!=null);
        System.out.println(field1);

        CThostFtdcDepthMarketDataField field2 = WebMarketDataProducer.line2field(str2);
        assertTrue(field2!=null);
        System.out.println(field2);

        matcher = WebMarketDataProducer.SINA_PATTERN.matcher(str1);
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

        CThostFtdcDepthMarketDataField field3 = WebMarketDataProducer.line2field(str3);
        assertTrue(field3!=null);
        System.out.println(field3);
    }

}
