package trader.service.md;

import java.util.Properties;

import trader.common.util.JsonEnabled;

/**
 * 一个行情数据源的运行时信息
 */
public interface MarketDataProducer extends JsonEnabled {
    /**
     * 唯一ID
     */
    public String getId();

    /**
     * 数据源类型: CTP/FEMAS/XTP等等
     */
    public String getType();

    /**
     * 连接参数
     */
    public Properties getConnectionProps();

    /**
     * 当前连接状态
     */
    public boolean isConnected();

}
