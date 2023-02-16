package trader.service.md;

import trader.common.util.NetUtil;
import trader.common.util.NetUtil.HttpMethod;
import trader.common.util.StringUtil;
import trader.service.md.web.WebMarketDataProducer;

public class WebMarketDataTest {

    public static void main(String[] args) throws Exception
    {
        String tt = NetUtil.readHttpAsText("http://hq.sinajs.cn/list=sh601398", HttpMethod.GET, null, StringUtil.GBK, WebMarketDataProducer.SINA_WEB_REFER);
        System.out.println(tt);
    }

}
