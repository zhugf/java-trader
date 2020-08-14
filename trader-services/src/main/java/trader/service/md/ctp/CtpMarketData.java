package trader.service.md.ctp;

import java.time.LocalDate;
import java.time.ZoneId;

import net.jctp.CThostFtdcDepthMarketDataField;
import trader.common.exchangeable.Exchange;
import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableType;
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

    public CtpMarketData(String producerId, Exchangeable instrument, CThostFtdcDepthMarketDataField data, LocalDate tradingDay) {
        this.producerId = producerId;
        this.field = data;
        this.instrument = instrument;
        this.volume = data.Volume;
        this.openInterest = (long)data.OpenInterest;
        this.lastPrice = PriceUtil.price2long(data.LastPrice);
        String actionDayStr = data.ActionDay;
        String tradingDayStr = data.TradingDay;
        int timeInt = DateUtil.time2int(data.UpdateTime);
        //周五夜市DCE的ActionDay提前3天, CZCE的TradingDay晚了3天, SHFE正常
        //2015-01-30 21:03:00 DCE ActionDay 20150202, TraingDay 20150202
        //2015-02-30 21:03:00 DCE ActionDay 20150130, TradingDay ""
        //2020-06-23 21:03:00 DCE ActionDay 20200623, TradingDay 20200624

        //2015-01-30 21:03:00 CZCE ActionDay 20150130, TraingDay 20150130
        //2015-01-30 21:03:00 SHFE ActionDay 20150130, TraingDay 20150202
        if ( instrument.exchange()==Exchange.DCE ) {
            //DCE的ActionDay, 夜市的值实际上是TradingDay
            if (timeInt >= 80000 && timeInt <= 185000) {
               // 日市tradingDay==actionDay, 不做任何修改
            }  else {
                if (StringUtil.equals(actionDayStr, tradingDayStr)) {
                    //2015-01-30 21:03:00 DCE ActionDay 20150202, TraingDay 20150202
                    // 夜市 tradingDay-1 = actionDay
                    LocalDate actionDay = MarketDayUtil.prevMarketDay(Exchange.DCE, DateUtil.str2localdate(tradingDayStr));
                    // 夜市的00:0002:30, 夜市后半场
                    if (timeInt < 30000) {
                        actionDay = actionDay.plusDays(1);
                    }
                    actionDayStr = DateUtil.date2str(actionDay);
                } else if (StringUtil.isEmpty(tradingDayStr)) {
                    //2015-02-30 21:03:00 DCE ActionDay 20150130, TradingDay ""
                    LocalDate tradingDay0 = MarketDayUtil.nextMarketDay(Exchange.DCE, DateUtil.str2localdate(actionDayStr));
                    tradingDayStr = DateUtil.date2str(tradingDay0);
                } else {
                    //2020-06-23 21:03:00 DCE ActionDay 20200623, TradingDay 20200624
                    //不做修改
                }
            }
        } else if ( instrument.exchange()==Exchange.CZCE ) {
            //组合行情的actionDay为空
            if ( instrument.getType()==ExchangeableType.FUTURE_COMBO && StringUtil.isEmpty(actionDayStr) ) {
                if ( timeInt>= 150000 ) {
                    LocalDate actionDay0 = MarketDayUtil.prevMarketDay(Exchange.CZCE, DateUtil.str2localdate(tradingDayStr));
                    actionDayStr = DateUtil.date2str(actionDay0);
                } else {
                    actionDayStr = tradingDayStr;
                }
            }
            //CZCE的tradingDay是actionDay, 需要判断后加以识别
            tradingDayStr = DateUtil.date2str(tradingDay);
            //CZCE 每天早上推送一条昨晚夜市收盘的价格, 但是ActionDay/TradingDay 都是当天白天日市数据
            if ( PriceUtil.isValidPrice(data.ClosePrice) && data.UpdateTime.compareTo("15")>0 ) { //日市会将夜市的ClosePrice记录下来
                LocalDate actionDay0 = MarketDayUtil.prevMarketDay(Exchange.CZCE, tradingDay);
                actionDayStr = DateUtil.date2str(actionDay0);
            }
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
        //CTP的市场均价需要除以合约乘数, 郑州所除外
        int volumeMultiplier = this.instrument.getVolumeMutiplier();
        if ( instrument.exchange()==Exchange.CZCE ) {
            this.averagePrice = PriceUtil.price2long(data.AveragePrice);
            this.turnover = PriceUtil.price2long(data.Turnover)*volumeMultiplier;
        } else {
            this.turnover = PriceUtil.price2long(data.Turnover);
            this.averagePrice = PriceUtil.price2long(data.AveragePrice)/volumeMultiplier;
        }
        this.tradingDay = tradingDayStr;
        this.upperLimitPrice = PriceUtil.price2long(data.UpperLimitPrice);
        this.lowerLimitPrice = PriceUtil.price2long(data.LowerLimitPrice);
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
        CtpMarketData obj = new CtpMarketData(producerId, instrument, field, DateUtil.str2localdate(tradingDay));
        cloneImpl(obj);
        return obj;
    }

}
