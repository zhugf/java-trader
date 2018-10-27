package trader.service.md;

import java.time.LocalDateTime;

import trader.common.exchangeable.Exchangeable;
import trader.common.util.FormatUtil;

/**
 * 市场行情数据对象
 */
public abstract class MarketData implements Cloneable {

    /**
     * Producer Id
     */
    public String producerId;

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
     * 最后修改时间
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
}
