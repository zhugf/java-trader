package trader.service.md;

import static org.junit.Assert.assertTrue;

import java.time.LocalDate;

import org.junit.Test;

import net.jctp.CThostFtdcDepthMarketDataField;
import trader.common.exchangeable.Exchangeable;
import trader.common.util.CSVDataSet;
import trader.common.util.CSVUtil;
import trader.common.util.DateUtil;
import trader.common.util.csv.CtpCSVMarshallHelper;
import trader.service.md.ctp.CtpMarketData;

public class CtpMarketDataTest {

    @Test
    public void test() {
        Exchangeable SR001 = Exchangeable.fromString("SR001");
        LocalDate tradingDay = DateUtil.str2localdate("20190902");
        String csvText = "TradingDay,InstrumentID,ExchangeID,ExchangeInstID,LastPrice,PreSettlementPrice,PreClosePrice,PreOpenInterest,OpenPrice,HighestPrice,LowestPrice,Volume,Turnover,OpenInterest,ClosePrice,SettlementPrice,UpperLimitPrice,LowerLimitPrice,PreDelta,CurrDelta,UpdateTime,UpdateMillisec,BidPrice1,BidVolume1,AskPrice1,AskVolume1,BidPrice2,BidVolume2,AskPrice2,AskVolume2,BidPrice3,BidVolume3,AskPrice3,AskVolume3,BidPrice4,BidVolume4,AskPrice4,AskVolume4,BidPrice5,BidVolume5,AskPrice5,AskVolume5,AveragePrice,ActionDay\n"
               +"20190830,SR001,,,5390.00,5364.00,5368.00,643320.00,5366.00,5403.00,5357.00,297788,1601801652.00,644542.00,0.00,N/A,5579.00,5149.00,0.00,0.00,23:29:59,000,5390.00,3,5391.00,36,0.00,0,0.00,0,0.00,0,0.00,0,0.00,0,0.00,0,0.00,0,0.00,0,5379.00,20190830\n" +
               "20190902,SR001,,,5390.00,5364.00,5368.00,643320.00,5366.00,5403.00,5357.00,297788,1601801652.00,644542.00,5390.00,5379.00,5579.00,5149.00,0.00,0.00,23:29:59,000,5390.00,3,5391.00,36,0.00,0,0.00,0,0.00,0,0.00,0,0.00,0,0.00,0,0.00,0,0.00,0,5379.00,20190902\n"+
               "20190902,SR001,,,5390.00,5364.00,5368.00,643320.00,5366.00,5403.00,5357.00,297794,1601833926.00,644542.00,5390.00,5379.00,5579.00,5149.00,0.00,0.00,09:00:00,000,5389.00,17,5390.00,5,0.00,0,0.00,0,0.00,0,0.00,0,0.00,0,0.00,0,0.00,0,0.00,0,5379.00,20190902\n"+
               "";

        CtpCSVMarshallHelper helper = new CtpCSVMarshallHelper();

        CSVDataSet ds = CSVUtil.parse(csvText);

        //夜市
        ds.next();
        CThostFtdcDepthMarketDataField tick = helper.unmarshall(ds.getRow());
        System.out.println(tick);
        CtpMarketData ctpTick = new CtpMarketData("ctp", SR001, tick, tradingDay);
        System.out.println(ctpTick.updateTime);
        assertTrue(ctpTick.tradingDay.equals("20190902"));
        assertTrue(ctpTick.updateTime.toLocalDate().equals(DateUtil.str2localdate("20190830")));
        System.out.println();

        //日市时推送夜市数据
        ds.next();
        tick = helper.unmarshall(ds.getRow());
        System.out.println(tick);
        ctpTick = new CtpMarketData("ctp", SR001, tick, tradingDay);
        System.out.println(ctpTick.updateTime);
        assertTrue(ctpTick.tradingDay.equals("20190902"));
        assertTrue(ctpTick.updateTime.toLocalDate().equals(DateUtil.str2localdate("20190830")));
        System.out.println();

        //正常日市数据
        ds.next();
        tick = helper.unmarshall(ds.getRow());
        System.out.println(tick);
        ctpTick = new CtpMarketData("ctp", SR001, tick, tradingDay);
        System.out.println(ctpTick.updateTime);
        assertTrue(ctpTick.tradingDay.equals("20190902"));
        assertTrue(ctpTick.updateTime.toLocalDate().equals(DateUtil.str2localdate("20190902")));
        System.out.println();
    }
}
