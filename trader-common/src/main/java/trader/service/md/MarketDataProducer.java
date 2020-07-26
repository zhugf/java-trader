package trader.service.md;

import java.time.LocalDate;
import java.util.Properties;

import trader.common.beans.Identifiable;
import trader.common.exchangeable.Exchangeable;
import trader.common.util.JsonEnabled;
import trader.service.ServiceConstants.ConnState;

/**
 * 一个行情数据源的运行时信息
 */
public interface MarketDataProducer<T> extends Identifiable, JsonEnabled {

    /**
     * SFIT CTP
     */
    public static final String PROVIDER_CTP = "ctp";
    /**
     * 飞马
     */
    public static final String PROVIDER_FEMAS = "femas";
    /**
     * 易盛
     */
    public static final String PROVIDER_TAP = "tap";
    /**
     * TDX
     */
    public static final String PROVIDER_TDX = "tdx";
    /**
     * WEB: sina
     */
    public static final String PROVIDER_WEB = "web";

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
     *
     * @return MarketData 或 null
     */
    public MarketData createMarketData(T rawMarketData, LocalDate tradingDay);
}
