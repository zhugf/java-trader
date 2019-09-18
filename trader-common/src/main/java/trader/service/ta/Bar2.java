package trader.service.ta;

import org.ta4j.core.Bar;
import org.ta4j.core.num.Num;

import trader.common.exchangeable.ExchangeableTradingTimes;
import trader.service.md.MarketData;

public interface Bar2 extends Bar {

    /**
     * 该交易日的时间
     */
    public ExchangeableTradingTimes getTradingTimes();

    /**
     * 该交易日的位置
     */
    public int getIndex();

    /**
     * Bar均价
     */
    public Num getAvgPrice();

    /**
     * 市场均价
     */
    public Num getMktAvgPrice();

    /**
     * 开仓手数
     */
    public long getOpenInterest();

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
