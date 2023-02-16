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
        String str1 = "var hq_str_sz300979=\"华利集团,60.530,60.530,60.250,61.680,60.050,60.250,60.290,677499,41344095.150,500,60.250,700,60.240,400,60.230,400,60.220,700,60.200,300,60.290,400,60.300,500,60.310,600,60.320,1400,60.330,2023-02-16,14:33:24,00\"; ";
        String str2 = "var hq_str_sh000300=\"沪深300,4125.4087,4123.6893,4129.0071,4132.2958,4117.6013,0,0,18262492,30561513261,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2023-02-16,09:41:50,00,\";";
        String str3 = "var hq_str_sh000905=\"中证500,6384.2145,6384.3479,6380.8826,6388.1951,6379.5681,0,0,18049889,22262037350,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2023-02-16,09:41:50,00,\";";

        CThostFtdcDepthMarketDataField field1 = WebMarketDataProducer.sina2field(str1);

        WebMarketData tick = new WebMarketData("p1", new Security(Exchange.SSE, "601398"), field1);
        assertTrue(field1!=null);
        System.out.println(field1);

        CThostFtdcDepthMarketDataField field2 = WebMarketDataProducer.sina2field(str2);
        assertTrue(field2!=null);
        System.out.println(field2);

        CThostFtdcDepthMarketDataField field3 = WebMarketDataProducer.sina2field(str3);
        assertTrue(field3!=null);
        System.out.println(field3);
    }

    @Test
    public void testTencentDataParsing() {
        String str1 = "v_sz000858=\"51~五 粮 液~000858~210.79~211.00~209.35~213380~108681~104698~210.79~32~210.78~26~210.77~9~210.76~26~210.75~28~210.80~415~210.81~210~210.82~96~210.83~11~210.85~61~~20230216150215~-0.21~-0.10~214.68~207.80~210.79/213380/4522470620~213380~452247~0.55~31.42~~214.68~207.80~3.26~8181.70~8182.04~7.62~232.10~189.90~1.04~-672~211.94~30.70~35.00~~~1.43~452247.0620~0.0000~0~ ~GP-A~16.66~1.64~1.43~24.26~20.27~219.89~132.33~-0.81~9.00~36.69~3881445240~3881608005~-73.52~34.77~3881445240~~~7.33~-0.03~~CNY\";";
        String str2 = "v_sh000852=\"1~中证1000~000852~7091.28~7063.60~7063.93~102363493~51181747~51181747~0.00~0~0.00~0~0.00~0~0.00~0~0.00~0~0.00~0~0.00~0~0.00~0~0.00~0~0.00~0~~20230216120500~27.68~0.39~7093.55~7040.47~7091.28/102363493/128099353316~102363493~12809935~1.13~31.65~~7093.55~7040.47~0.75~103237.28~128237.54~0.00~-1~-1~1.31~0~7069.54~~~~~~12809935.3316~0.0000~0~ ~ZS~12.89~1.32~~~~7481.14~5164.75~2.28~9.27~5.81~905079536960~~4.90~11.79~905079536960~~~-1.28~0.00~~CNY\";";

        CThostFtdcDepthMarketDataField field1 = WebMarketDataProducer.tencent2field(str1);
        assertTrue(null!=field1);
        CThostFtdcDepthMarketDataField field2 = WebMarketDataProducer.tencent2field(str2);
        assertTrue(null!=field2);
        assertTrue(field2.LastPrice==7091.28);
    }
}
