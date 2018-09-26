package trader.service.md;

import java.util.Collection;

import trader.common.exchangeable.Exchangeable;

/**
 * 行情服务.
 * <BR>从多个行情数据源整合并生成行情数据并回调, 自动合并相同tick
 */
public interface MarketDataService {

    /**
     * 当前的行情数据源状态
     */
    public Collection<MarketDataProducer> getProducers();

	public void addMarketDataListener(MarketDataListener listener, Exchangeable... exchangeables);

}
