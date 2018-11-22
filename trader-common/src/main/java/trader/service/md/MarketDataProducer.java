package trader.service.md;

import java.time.LocalDate;
import java.util.Properties;

import trader.common.exchangeable.Exchangeable;
import trader.common.util.JsonEnabled;
import trader.service.ServiceConstants.ConnState;

/**
 * 一个行情数据源的运行时信息
 */
public interface MarketDataProducer<T> extends JsonEnabled {

    public static final String PROVIDER_CTP = "ctp";

    /**
     * 唯一ID
     */
    public String getId();

    /**
     * 数据源类型: ctp/femas/xtp等等
     */
    public String getProvider();

    /**
     * 连接参数
     */
    public Properties getConnectionProps();

    /**
     * 连接状态
     */
    public ConnState getState();

    /**
     * 状态设置时间
     */
    public long getStateTime();

    /**
     * 检查是否可以订阅行情
     */
    public boolean canSubscribe(Exchangeable e);

    /**
     * 从原始行情事件对象创建MarketData对象
     */
    public MarketData createMarketData(T rawMarketData, LocalDate actionDay);
}
