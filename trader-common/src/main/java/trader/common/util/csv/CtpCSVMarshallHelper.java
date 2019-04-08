package trader.common.util.csv;

import java.text.Format;
import java.util.ArrayList;

import net.jctp.CThostFtdcDepthMarketDataField;
import trader.common.util.CSVMarshallHelper;
import trader.common.util.FormatUtil;
import trader.common.util.PriceUtil;

public class CtpCSVMarshallHelper implements CSVMarshallHelper<CThostFtdcDepthMarketDataField> {

	private static final String[] header = new String[]{
			"TradingDay"
            ,"InstrumentID"
            ,"ExchangeID"
            ,"ExchangeInstID"
            ,"LastPrice"
            ,"PreSettlementPrice"
            ,"PreClosePrice"
            ,"PreOpenInterest"
            ,"OpenPrice"
            ,"HighestPrice"
            ,"LowestPrice"
            ,"Volume"
            ,"Turnover"
            ,"OpenInterest"
            ,"ClosePrice"
            ,"SettlementPrice"
            ,"UpperLimitPrice"
            ,"LowerLimitPrice"
            ,"PreDelta"
            ,"CurrDelta"
            ,"UpdateTime"
            ,"UpdateMillisec"
            ,"BidPrice1"
            ,"BidVolume1"
            ,"AskPrice1"
            ,"AskVolume1"
            ,"BidPrice2"
            ,"BidVolume2"
            ,"AskPrice2"
            ,"AskVolume2"
            ,"BidPrice3"
            ,"BidVolume3"
            ,"AskPrice3"
            ,"AskVolume3"
            ,"BidPrice4"
            ,"BidVolume4"
            ,"AskPrice4"
            ,"AskVolume4"
            ,"BidPrice5"
            ,"BidVolume5"
            ,"AskPrice5"
            ,"AskVolume5"
            ,"AveragePrice"
            ,"ActionDay"
	};

	@Override
	public String[] getHeader() {
		return header;
	}

	@Override
	public CThostFtdcDepthMarketDataField unmarshall(String[] row) {
		int i=0;
		CThostFtdcDepthMarketDataField result = new CThostFtdcDepthMarketDataField();
	    result.TradingDay = row[i++];
		result.InstrumentID = row[i++];
		result.ExchangeID = row[i++];
		result.ExchangeInstID = row[i++];
		result.LastPrice = PriceUtil.str2price(row[i++]);
		result.PreSettlementPrice = PriceUtil.str2price(row[i++]);
		result.PreClosePrice = PriceUtil.str2price(row[i++]);
		result.PreOpenInterest = PriceUtil.str2price(row[i++]);
		result.OpenPrice = PriceUtil.str2price(row[i++]);
		result.HighestPrice = PriceUtil.str2price(row[i++]);
		result.LowestPrice = PriceUtil.str2price(row[i++]);
		result.Volume = Integer.parseInt(row[i++]);
		result.Turnover = PriceUtil.str2price(row[i++]);
		result.OpenInterest = PriceUtil.str2price(row[i++]);
		result.ClosePrice = PriceUtil.str2price(row[i++]);
		result.SettlementPrice = PriceUtil.str2price(row[i++]);
		result.UpperLimitPrice = PriceUtil.str2price(row[i++]);
		result.LowerLimitPrice = PriceUtil.str2price(row[i++]);
		result.PreDelta = PriceUtil.str2price(row[i++]);
		result.CurrDelta = PriceUtil.str2price(row[i++]);
		result.UpdateTime = row[i++];
		result.UpdateMillisec = Integer.parseInt(row[i++]);
		result.BidPrice1 = PriceUtil.str2price(row[i++]);
		result.BidVolume1 = Integer.parseInt(row[i++]);
		result.AskPrice1 = PriceUtil.str2price(row[i++]);
		result.BidVolume1 = Integer.parseInt(row[i++]);

		result.BidPrice2 = PriceUtil.str2price(row[i++]);
		result.BidVolume2 = Integer.parseInt(row[i++]);
		result.AskPrice2 = PriceUtil.str2price(row[i++]);
		result.BidVolume2 = Integer.parseInt(row[i++]);

		result.BidPrice3 = PriceUtil.str2price(row[i++]);
		result.BidVolume3 = Integer.parseInt(row[i++]);
		result.AskPrice3 = PriceUtil.str2price(row[i++]);
		result.BidVolume3 = Integer.parseInt(row[i++]);

		result.BidPrice4 = PriceUtil.str2price(row[i++]);
		result.BidVolume4 = Integer.parseInt(row[i++]);
		result.AskPrice4 = PriceUtil.str2price(row[i++]);
		result.BidVolume4 = Integer.parseInt(row[i++]);

		result.BidPrice5 = PriceUtil.str2price(row[i++]);
		result.BidVolume5 = Integer.parseInt(row[i++]);
		result.AskPrice5 = PriceUtil.str2price(row[i++]);
		result.BidVolume5 = Integer.parseInt(row[i++]);

		result.AveragePrice = PriceUtil.str2price(row[i++]);
		result.ActionDay = row[i++];
		return result;
	}

	@Override
	public String[] marshall(CThostFtdcDepthMarketDataField field) {
		ArrayList<String> row = new ArrayList<>();

        Format millisecFormat = FormatUtil.getDecimalFormat("000");

        	row.add(field.TradingDay);
            row.add(field.InstrumentID);
            row.add(field.ExchangeID);
            row.add(field.ExchangeInstID);
            row.add(PriceUtil.price2str(field.LastPrice));
            row.add(PriceUtil.price2str(field.PreSettlementPrice));
            row.add(PriceUtil.price2str(field.PreClosePrice));
            row.add(PriceUtil.price2str(field.PreOpenInterest));
            row.add(PriceUtil.price2str(field.OpenPrice));
            row.add(PriceUtil.price2str(field.HighestPrice));
            row.add(PriceUtil.price2str(field.LowestPrice));
            row.add(""+field.Volume);
            row.add(PriceUtil.price2str(field.Turnover));
            row.add(PriceUtil.price2str(field.OpenInterest));
            row.add(PriceUtil.price2str(field.ClosePrice));
            row.add(PriceUtil.price2str(field.SettlementPrice));
            row.add(PriceUtil.price2str(field.UpperLimitPrice));
            row.add(PriceUtil.price2str(field.LowerLimitPrice));
            row.add(PriceUtil.price2str(field.PreDelta));
            row.add(PriceUtil.price2str(field.CurrDelta));
            row.add(field.UpdateTime);
            row.add(millisecFormat.format(field.UpdateMillisec));
            row.add(PriceUtil.price2str(field.BidPrice1));
            row.add(""+field.BidVolume1);
            row.add(PriceUtil.price2str(field.AskPrice1));
            row.add(""+field.AskVolume1);
            row.add(PriceUtil.price2str(field.BidPrice2));
            row.add(""+field.BidVolume2);
            row.add(PriceUtil.price2str(field.AskPrice2));
            row.add(""+field.AskVolume2);
            row.add(PriceUtil.price2str(field.BidPrice3));
            row.add(""+field.BidVolume3);
            row.add(PriceUtil.price2str(field.AskPrice3));
            row.add(""+field.AskVolume3);
            row.add(PriceUtil.price2str(field.BidPrice4));
            row.add(""+field.BidVolume4);
            row.add(PriceUtil.price2str(field.AskPrice4));
            row.add(""+field.AskVolume4);
            row.add(PriceUtil.price2str(field.BidPrice5));
            row.add(""+field.BidVolume5);
            row.add(PriceUtil.price2str(field.AskPrice5));
            row.add(""+field.AskVolume5);
            row.add(PriceUtil.price2str(field.AveragePrice));
            row.add(field.ActionDay);

        return row.toArray(new String[row.size()]);
	}

}
