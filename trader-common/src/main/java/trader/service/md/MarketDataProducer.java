package trader.service.md;

import java.util.Properties;

import trader.common.exchangeable.Exchangeable;
import trader.common.util.JsonEnabled;

/**
 * 一个行情数据源的运行时信息
 */
public interface MarketDataProducer extends JsonEnabled {
    public static enum Type{ctp, femas};

    public static enum Status{
        /**
         * 已创建未连接
         */
        Initialized,
        /**
         * 连接和登录中
         */
        Connecting,
        /**
         * 已登录
         */
        Connected,
        /**
         * 已断开, 后续会自动重连
         */
        Disconnected,
        /**
         * 连接失败, 不会自动重连
         */
        ConnectFailed
        };

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
    public Status getStatus();

    /**
     * 状态设置时间
     */
    public long getStatusTime();

    /**
     * 检查是否可以订阅行情
     */
    public boolean canSubscribe(Exchangeable e);
}
