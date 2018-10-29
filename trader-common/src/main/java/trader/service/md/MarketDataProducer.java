package trader.service.md;

import java.util.Properties;

import trader.common.exchangeable.Exchangeable;
import trader.common.util.JsonEnabled;
import trader.service.ServiceConstants.ConnState;

/**
 * 一个行情数据源的运行时信息
 */
public interface MarketDataProducer<T> extends JsonEnabled {
    public static enum Type{ctp, femas};

    /**
     * 唯一ID
     */
    public String getId();

    /**
     * 数据源类型: CTP/FEMAS/XTP等等
     */
    public Type getType();

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
    public MarketData createMarketData(T rawMarketData);
}
