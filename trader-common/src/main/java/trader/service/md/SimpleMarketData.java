package trader.service.md;

/**
 * 用于实现从JSON数据还原MarketData
 */
public class SimpleMarketData extends MarketData {

    SimpleMarketData() {

    }

    @Override
    public MarketData clone() {
        return cloneImpl(new SimpleMarketData());
    }

    @Override
    public String getCsvHead() {
        return null;
    }

    @Override
    public void toCsvRow(StringBuilder rowBuf) {

    }

}
