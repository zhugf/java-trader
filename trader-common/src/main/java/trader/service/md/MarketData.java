package trader.service.md;

import java.time.LocalDateTime;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import trader.common.exchangeable.Exchangeable;
import trader.common.util.DateUtil;
import trader.common.util.FormatUtil;
import trader.common.util.JsonEnabled;
import trader.common.util.PriceUtil;

/**
 * 市场行情数据对象
 */
public abstract class MarketData implements Cloneable, JsonEnabled {

    /**
     * Producer Id
     */
    public String producerId;

    /**
     * 交易日
     */
    public String tradingDay;

    /**
     * 合约
     */
    public Exchangeable instrumentId;

    /**
     * 数量
     */
    public long volume;

    /**
     * 成交金额
     */
    public long turnover;

    /**
     * 持仓量
     */
    public long openInterest;

    /**
     * 最新价
     */
    public long lastPrice;

    /**
     * 最后修改时间
     */
    public LocalDateTime updateTime;

    /**
     * 最后修改时间(EpochMillis)
     */
    public long updateTimestamp;

    /**
     * 昨收
     */
    public long preClosePrice;

    /**
     * 今开
     */
    public long openPrice;

    /**
     * 最高
     */
    public long highestPrice;

    /**
     *　最低
     */
    public long lowestPrice;

    /**
     * 当日均价
     */
    public long averagePrice;

    /**
     * 当日最高限价
     */
    public long upperLimitPrice;

    /**
     * 当日最低限价
     */
    public long lowerLimitPrice;

    /**
     * 行情深度:多少档位
     */
    public int depth;

    /**
     * 申买价: 1-10
     */
    public long bidPrices[];
    /**
     * 申买量: 1-10
     */
    public int bidVolumes[];
    /**
     * 申买数: 1-10;
     */
    public int bidCounts[];

    /**
     * 申卖价: 1-10
     */
    public long askPrices[];
    /**
     * 申卖量: 1-10
     */
    public int askVolumes[];
    /**
     * 申卖量: 1-10
     */
    public int askCounts[];

    public abstract String getCsvHead();

    public abstract void toCsvRow(StringBuilder rowBuf);

    public long lastAskPrice(){
        if ( askPrices!=null && askPrices.length>0 ){
            return askPrices[0];
        }
        return lastPrice;
    }

    public long lastBidPrice(){
        if ( bidPrices!=null && bidPrices.length>0 ){
            return bidPrices[0];
        }
        return lastPrice;
    }

    protected static String millisec2str(int millisec){
        return FormatUtil.getDecimalFormat("000") .format(millisec);
    }

    protected static String price2str(double price){
        if ( price==Double.MAX_VALUE||price==Double.NaN ) {
            return "";
        }
        return FormatUtil.getDecimalFormat("###########0.0#").format(price);
    }

    protected void cloneImpl(MarketData marketDataToClone){
        marketDataToClone.producerId = producerId;
        marketDataToClone.instrumentId = instrumentId;
        marketDataToClone.volume = volume;
        marketDataToClone.turnover = turnover;
        marketDataToClone.openInterest = openInterest;
        marketDataToClone.lastPrice = lastPrice;
        marketDataToClone.updateTime = updateTime;
        marketDataToClone.preClosePrice = preClosePrice;
        marketDataToClone.openPrice = openPrice;
        marketDataToClone.highestPrice = highestPrice;
        marketDataToClone.lowestPrice = lowestPrice;
        marketDataToClone.averagePrice = averagePrice;
        marketDataToClone.depth = depth;
        marketDataToClone.bidPrices = bidPrices;
        marketDataToClone.bidVolumes = bidVolumes;
        marketDataToClone.bidCounts = bidCounts;
        marketDataToClone.askPrices = askPrices;
        marketDataToClone.askVolumes = askVolumes;
        marketDataToClone.askCounts = askCounts;
    }

    @Override
    public abstract MarketData clone();

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("instrumentId", instrumentId.toString());
        json.addProperty("producerId", producerId.toString());
        json.addProperty("tradingDay", tradingDay);
        json.addProperty("volume", volume);
        json.addProperty("openInterest", openInterest);
        json.addProperty("updateTime", DateUtil.date2str(updateTime));
        json.addProperty("updateTimestamp", updateTimestamp);
        json.addProperty("turnover", PriceUtil.long2str(turnover));
        json.addProperty("lastPrice", PriceUtil.long2str(lastPrice));
        json.addProperty("openPrice", PriceUtil.long2str(openPrice));
        json.addProperty("highestPrice", PriceUtil.long2str(highestPrice));
        json.addProperty("lowestPrice", PriceUtil.long2str(lowestPrice));
        json.addProperty("averagePrice", PriceUtil.long2str(averagePrice));

        JsonArray array = new JsonArray();
        for(int i=0;i<depth;i++) {
            array.add(PriceUtil.long2str(bidPrices[i]));
        }
        json.add("bidPrices", array);

        array = new JsonArray();
        for(int i=0;i<depth;i++) {
            array.add((bidVolumes[i]));
        }
        json.add("bidVolumes", array);
        if ( bidCounts!=null ) {
            array = new JsonArray();
            for(int i=0;i<depth;i++) {
                array.add((bidCounts[i]));
            }
            json.add("bidCounts", array);
        }
        array = new JsonArray();
        for(int i=0;i<depth;i++) {
            array.add(PriceUtil.long2str(askPrices[i]));
        }
        json.add("askPrices", array);

        array = new JsonArray();
        for(int i=0;i<depth;i++) {
            array.add((askVolumes[i]));
        }
        json.add("askVolumes", array);

        if ( askCounts!=null ) {
            array = new JsonArray();
            for(int i=0;i<depth;i++) {
                array.add((askCounts[i]));
            }
            json.add("askCounts", array);
        }
        return json;
    }

    @Override
    public String toString() {
        return "MD["+instrumentId+" "+DateUtil.date2str(updateTime)+" "+PriceUtil.long2str(lastPrice)+"]";
    }

}
