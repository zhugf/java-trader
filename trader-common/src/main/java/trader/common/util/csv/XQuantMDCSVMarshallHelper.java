package trader.common.util.csv;

import trader.common.util.CSVMarshallHelper;
import trader.common.util.DateUtil;
import trader.common.util.PriceUtil;

public class XQuantMDCSVMarshallHelper implements CSVMarshallHelper<XQuantMD> {

    public static final String HEADER = "Id,Instrument,Timestamp,PreSettlementPrice,Open,High,Low,YesterdayClosePrice,YesterdayOpenInt,SettlementPrice,OpenInt,UpperLimitPrice,LowerLimitPrice,LatestPrice,LatestVol,Vol,Turnover,AvgLatestPrice,AskPrice1,AskVol1,BidPrice1,BidVol1";

    @Override
    public String[] getHeader() {
        return HEADER.split(",");
    }

    @Override
    public XQuantMD unmarshall(String[] row) {
        int i=0;
        XQuantMD r = new XQuantMD();
        r.timestamp = DateUtil.str2localdatetime(row[i++]);
        r.Open = PriceUtil.str2long(row[i++]);
        r.High = PriceUtil.str2long(row[i++]);
        r.Low = PriceUtil.str2long(row[i++]);

        r.LatestPrice = PriceUtil.str2long(row[i++]);
        r.LatestVol = Integer.parseInt(row[i++]);
        r.Turnover = PriceUtil.str2long(row[i++]);
        r.Vol = Integer.parseInt(row[i++]);
        r.OpenInt = Integer.parseInt(row[i++]);
        r.AvgLatestPrice = PriceUtil.str2long(row[i++]);

        r.AskPrice1 = PriceUtil.str2long(row[i++]);
        r.AskVol1 = Integer.parseInt(row[i++]);
        r.BidPrice1 = PriceUtil.str2long(row[i++]);
        r.BidVol1 = Integer.parseInt(row[i++]);

        r.YesterdayClosePrice = PriceUtil.str2long(row[i++]);
        r.YesterdayOpenInt = Integer.parseInt(row[i++]);
        r.PreSettlementPrice = PriceUtil.str2long(row[i++]);

        r.TodayClosePrice = PriceUtil.str2long(row[i++]);
        r.SettlementPrice = PriceUtil.str2long(row[i++]);
        r.UpperLimitPrice = PriceUtil.str2long(row[i++]);
        r.LowerLimitPrice = PriceUtil.str2long(row[i++]);
        return r;
    }

    @Override
    public String[] marshall(XQuantMD t) {
        return null;
    }

}
