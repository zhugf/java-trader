package trader.service.ta;

import org.ta4j.core.Bar;
import org.ta4j.core.num.Num;

import trader.service.md.MarketData;

public interface Bar2 extends Bar {

    public Num getAvgPrice();

    public Num getMktAvgPrice();

    public long getOpenInterest();

    public MarketData getOpenTick();

    public MarketData getCloseTick();

}
