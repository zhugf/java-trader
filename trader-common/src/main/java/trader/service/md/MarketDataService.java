package trader.service.md;

import java.util.Collection;
import java.util.Map;

import trader.common.beans.ServiceStateAware;
import trader.common.exchangeable.Exchange;
import trader.common.exchangeable.Exchangeable;

/**
 * 行情服务.
 * <BR>从多个行情数据源整合并生成行情数据并回调, 自动合并相同tick
 */
public interface MarketDataService extends ServiceStateAware {

    public Map<String, MarketDataProducerFactory> getProducerFactories();

    /**
     * 返回当天的主力合约, 从网络实时查询得到
     */
    public Collection<Exchangeable> getPrimaryInstruments();

    /**
     * 返回期货品种对应的主力合约
     *
     * @param exchange 交易所, 可以为null, 此时自动从合约名称关联
     * @param commodity 合约名称, ru代表主力合约 ru2代表次主力合约
     */
    public Exchangeable getPrimaryInstrument(Exchange exchange, String commodity);

    /**
     * 当前的行情数据源状态
     */
    public Collection<MarketDataProducer> getProducers();

    /**
     * 当前已配置的行情数据源
     */
    public MarketDataProducer getProducer(String producerId);

    /**
     * 当前已订阅品种
     */
    public Collection<Exchangeable> getSubscriptions();

    /**
     * 最后行情数据
     */
    public MarketData getLastData(Exchangeable instrument);

    /**
     * 增加主动订阅品种
     */
    public void addSubscriptions(Collection<Exchangeable> subscriptions);

    /**
     * 行情回调接口, 如果exchangables==null, 那么所有的行情都会被调用.
     * <BR>多线程模型: 行情回调接口从AsyncEventService的FILTER_CHAIN_MAIN线程调用, 因此不存在多线程同步问题, 但是需要保证处理代码不存在任何阻塞操作.
     */
    public void addListener(MarketDataListener listener, Exchangeable... exchangeables);

}
