package trader.service.md.ctp;

import java.time.LocalDate;
import java.time.ZoneId;

import net.jctp.CThostFtdcDepthMarketDataField;
import trader.common.exchangeable.Exchange;
import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.MarketDayUtil;
import trader.common.util.DateUtil;
import trader.common.util.PriceUtil;
import trader.common.util.StringUtil;
import trader.common.util.csv.CtpCSVMarshallHelper;
import trader.service.md.MarketData;

public class CtpMarketData extends MarketData {
    private static final CtpCSVMarshallHelper csvMarshallHelper = new CtpCSVMarshallHelper();
    private static final ZoneId CFFEX_ZONE_ID = Exchange.CFFEX.getZoneId();

    CThostFtdcDepthMarketDataField field;

    public CtpMarketData(String producerId, Exchangeable exchangeable, CThostFtdcDepthMarketDataField data, LocalDate tradingDay) {
        this.producerId = producerId;
        this.field = data;
        this.instrumentId = exchangeable;
        this.volume = data.Volume;
        this.turnover = PriceUtil.price2long(data.Turnover);
        this.openInterest = (long)data.OpenInterest;
        this.lastPrice = PriceUtil.price2long(data.LastPrice);
        String actionDayStr = data.ActionDay;
        String tradingDayStr = data.TradingDay;
        if ( exchangeable.exchange()==Exchange.DCE ) {
            //DCE的ActionDay, 夜市的值实际上是TradignDay
            int timeInt = DateUtil.time2int(data.UpdateTime);
            if (timeInt >= 80000 && timeInt <= 185000) {
                // 日市tradingDay==actionDay, 不做任何修改
            } else {
                // 夜市 tradingDay-1 = actionDay
                LocalDate actionDay = MarketDayUtil.prevMarketDay(exchangeable.exchange(), DateUtil.str2localdate(data.TradingDay));
                // 夜市的00:0002:30, 夜市后半场
                if (timeInt < 30000) {
                    actionDay = actionDay.plusDays(1);
                }
                actionDayStr = DateUtil.date2str(actionDay);
            }
        } else if ( exchangeable.exchange()==Exchange.CZCE ) {
            //CZCE的tradingDay是actionDay, 需要判断后加以识别
            tradingDayStr = DateUtil.date2str(tradingDay);
        }
        if ( StringUtil.isEmpty(tradingDayStr)) {
            tradingDayStr = DateUtil.date2str(tradingDay);
        }

        this.updateTime = DateUtil.str2localdatetime(actionDayStr, data.UpdateTime, data.UpdateMillisec);
        this.updateTimestamp = DateUtil.localdatetime2long(CFFEX_ZONE_ID, updateTime);
        this.preClosePrice = PriceUtil.price2long(data.PreClosePrice);
        this.openPrice = PriceUtil.price2long(data.OpenPrice);
        this.highestPrice = PriceUtil.price2long(data.HighestPrice);
        this.lowestPrice = PriceUtil.price2long(data.LowestPrice);
        this.averagePrice = PriceUtil.price2long(data.AveragePrice);
        this.tradingDay = tradingDayStr;

        long bidPrice2 = PriceUtil.price2long(data.BidPrice2);
        if (bidPrice2 == Long.MAX_VALUE || bidPrice2==0) {
            this.depth = 1;
            bidPrices = new long[] { PriceUtil.price2long(data.BidPrice1) };
            bidVolumes = new int[] { data.BidVolume1 };
            askPrices = new long[] { PriceUtil.price2long(data.AskPrice1) };
            askVolumes = new int[] { data.AskVolume1 };
        } else {
            this.depth = 5;
            long[] bidPrices = new long[5];
            bidPrices[0] = PriceUtil.price2long(data.BidPrice1);
            bidPrices[1] = bidPrice2;
            bidPrices[2] = PriceUtil.price2long(data.BidPrice3);
            bidPrices[3] = PriceUtil.price2long(data.BidPrice4);
            bidPrices[4] = PriceUtil.price2long(data.BidPrice5);
            this.bidPrices = bidPrices;

            int[] bidVolumes = new int[5];
            bidVolumes[0] = data.BidVolume1;
            bidVolumes[1] = data.BidVolume2;
            bidVolumes[2] = data.BidVolume3;
            bidVolumes[3] = data.BidVolume4;
            bidVolumes[4] = data.BidVolume5;
            this.bidVolumes = bidVolumes;

            long[] askPrices = new long[5];
            askPrices[0] = PriceUtil.price2long(data.AskPrice1);
            askPrices[1] = PriceUtil.price2long(data.AskPrice2);
            askPrices[2] = PriceUtil.price2long(data.AskPrice3);
            askPrices[3] = PriceUtil.price2long(data.AskPrice4);
            askPrices[4] = PriceUtil.price2long(data.AskPrice5);
            this.askPrices = askPrices;

            int[] askVolumes = new int[5];
            askVolumes[0] = data.AskVolume1;
            askVolumes[1] = data.AskVolume2;
            askVolumes[2] = data.AskVolume3;
            askVolumes[3] = data.AskVolume4;
            askVolumes[4] = data.AskVolume5;
            this.askVolumes = askVolumes;
        }
    }

    @Override
    public String getCsvHead() {
        StringBuilder header = new StringBuilder();
        for(String h:csvMarshallHelper.getHeader()){
            if ( header.length()>0 ){
                header.append(",");
            }
            header.append(h);
        }
        return header.toString();
    }

    @Override
    public void toCsvRow(StringBuilder rowBuf) {
        String[] fields = csvMarshallHelper.marshall(field);
        for(int i=0;i<fields.length;i++) {
            if ( i>0){
                rowBuf.append(",");
            }
            rowBuf.append(fields[i]);
        }
    }

    @Override
    public MarketData clone() {
        CtpMarketData obj = new CtpMarketData(producerId, instrumentId, field, DateUtil.str2localdate(tradingDay));
        cloneImpl(obj);
        return obj;
    }

}
