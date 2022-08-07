package trader.service.ta;

import org.ta4j.core.Bar;
import org.ta4j.core.num.Num;

import trader.common.exchangeable.ExchangeableTradingTimes;
import trader.service.md.MarketData;

public interface FutureBar extends Bar {

    /**
     * 该交易日的时间
     */
    public ExchangeableTradingTimes getTradingTimes();

    /**
     * 该交易日的位置
     */
    public int getIndex();

    /**
     * Bar>=95%成交量价位
     */
    //public Num get95PHighPrice();

    /**
     * Bar<=95%成交量价位
     */
    //public Num get95PLowPrice();

    /**
     * Bar均价
     */
    public Num getAvgPrice();

    /**
     * 市场均价
     */
    public Num getMktAvgPrice();

    /**
     * 开仓手数变化
     */
    public long getOpenInt();

    /**
     * 开始的持仓手数
     */
    public long getBeginOpenInt();

    /**
     * 最后的持仓手数
     */
    public long getEndOpenInt();

    /**
     * Bar开始TICK
     */
    public MarketData getOpenTick();

    /**
     * Bar结束TICK
     * @return
     */
    public MarketData getCloseTick();

    public MarketData getMaxTick();

    public MarketData getMinTick();

}
