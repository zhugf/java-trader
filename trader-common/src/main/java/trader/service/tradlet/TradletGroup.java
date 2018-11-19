package trader.service.tradlet;

import java.util.List;
import java.util.Properties;

import trader.common.exchangeable.Exchangeable;
import trader.common.util.JsonEnabled;
import trader.service.data.KVStore;
import trader.service.trade.AccountView;

/**
 * 交易策略分组, 一组开平仓策略以及相关配置参数构成一个完整的交易模型,
 * 可以附加一些额外的简单逻辑控制和事件回调函数. 支持动态修改
 */
public interface TradletGroup extends JsonEnabled {

    /**
     * 分组ID
     */
    public String getId();

    /**
     * 账户视图
     */
    public AccountView getAccountView();

    /**
     * 可交易品种
     */
    public List<Exchangeable> getExchangeables();

    /**
     * 配置参数
     */
    public Properties getProperties();

    /**
     * 交易策略列表
     */
    public List<Tradlet> getTradlets();

    /**
     * 是否启用
     */
    public boolean isEnabled();

    /**
     * 设置启用/禁用
     */
    public void setEnabled(boolean value);

    /**
     * 返回Group特有的KVStore
     */
    public KVStore getKVStore();
}
