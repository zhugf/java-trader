package trader.service.md;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        assertTrue(primaryInstruments2.size()+20 >= 3*primaryInstruments.size() );
        assertTrue(primaryInstruments2.size() >= 2*primaryInstruments.size() );

        System.out.println(primaryInstruments.size()+" : "+primaryInstruments);
        System.out.println(primaryInstruments2.size()+" : "+primaryInstruments2);
    }

    @Test
    public void test() {
        AppException ap = new AppException(ERR_MD_PRODUCER_DISCONNECTED, "Producer test is disconnected.");
        System.out.println(ap.getMessage());
    }

    public static final Pattern SINA_PATTERN = Pattern.compile("var hq_str_(?<InstrumentId>s[h|z]\\d{6})=\"(?<InstrumentName>[^,]+),(?<OpenPrice>\\d+(\\.\\d+)?),(?<PreClosePrice>\\d+(\\.\\d+)?),(?<LastPrice>\\d+(\\.\\d+)?),(?<HighestPrice>\\d+(\\.\\d+)?),(?<LowestPrice>\\d+(\\.\\d+)?),(?<BidPrice>\\d+(\\.\\d+)?),(?<AskPrice>\\d+(\\.\\d+)?),(?<Volume>\\d+),(?<Turnover>\\d+(\\.\\d+)?),(?<BidVolume1>\\d+),(?<BidPrice1>\\d+(\\.\\d+)?),(?<BidVolume2>\\d+),(?<BidPrice2>\\d+(\\.\\d+)?),(?<BidVolume3>\\d+),(?<BidPrice3>\\d+(\\.\\d+)?),(?<BidVolume4>\\d+),(?<BidPrice4>\\d+(\\.\\d+)?),(?<BidVolume5>\\d+),(?<BidPrice5>\\d+(\\.\\d+)?),(?<AskVolume1>\\d+),(?<AskPrice1>\\d+(\\.\\d+)?),(?<AskVolume2>\\d+),(?<AskPrice2>\\d+(\\.\\d+)?),(?<AskVolume3>\\d+),(?<AskPrice3>\\d+(\\.\\d+)?),(?<AskVolume4>\\d+),(?<AskPrice4>\\d+(\\.\\d+)?),(?<AskVolume5>\\d+),(?<AskPrice5>\\d+(\\.\\d+)?),(?<TradingDay>\\d{4}-\\d{2}-\\d{2}),(?<UpdateTime>\\d{2}:\\d{2}:\\d{2}),\\d{2}\";");

    @Test
    public void testSinaDataParsing() {

        String str1 = "var hq_str_sh601398=\"工商银行,5.650,5.670,5.600,5.660,5.590,5.600,5.610,44060844,247480554.000,882100,5.600,5442066,5.590,3206000,5.580,996400,5.570,891400,5.560,1927136,5.610,1528700,5.620,1659300,5.630,2949500,5.640,2904400,5.650,2019-07-08,09:50:02,00\";";
        String str2 = "var hq_str_sz000002=\"万 科Ａ,29.450,29.550,29.540,29.680,29.130,29.500,29.540,29796296,875728143.350,109700,29.500,8900,29.490,6400,29.480,300,29.470,4000,29.460,800,29.540,20312,29.550,29898,29.560,12300,29.570,18600,29.580,2019-07-05,14:20:57,00\";";
        String str3 = "var hq_str_sh000001=\"上证指数,2997.8067,3011.0588,2988.9717,2997.8067,2988.9388,0,0,8478274,8331208920,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2019-07-08,09:32:38,00\";";

        Matcher matcher = SINA_PATTERN.matcher(str3);
        assertTrue(matcher.matches());
        assertTrue(matcher.group("Volume").equals("8478274"));
        Pattern pattern = Pattern.compile("(?<PreClosePrice>\\d+(\\.\\d+)?)");
        assertTrue(pattern.matcher("0.000").matches());
        assertTrue(pattern.matcher("0").matches());

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
