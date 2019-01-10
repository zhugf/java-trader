package trader.service.tradlet;

import java.util.List;

import trader.common.exchangeable.Exchangeable;
import trader.common.util.JsonEnabled;
import trader.service.data.KVStore;
import trader.service.trade.Account;

/**
 * 交易策略分组, 一组开平仓策略以及相关配置参数构成一个完整的交易模型, 可以附加一些额外的简单逻辑控制和事件回调函数. 支持动态修改
 */
public interface TradletGroup extends JsonEnabled {

    /**
     * 状态顺序越低, 能做的事情越少
     */
    public static enum State {
        /**
         * 完全停止行情和交易的数据处理
         */
        Disabled,
        /**
         * 已暂停, 只可以接收行情数据, 更新内部状态, 不允许开平仓
         */
        Suspended,
        /**
         * 只允许平仓
         */
        CloseOnly,
        /**
         * 正常工作, 可开平仓
         */
        Enabled
    };

    /**
     * 分组ID
     */
    public String getId();

    /**
     * 账户视图
     */
    public Account getAccount();

    /**
     * 可交易品种
     */
    public Exchangeable getExchangeable();

    /**
     * 交易策略列表.
     * <BR>注意該函數在Tradlet.init()中无法运用
     */
    public List<Tradlet> getTradlets();

    /**
     * 获取配置状态
     */
    public State getConfigState();

    /**
     * 当前状态
     */
    public State getState();

    /**
     * 设置启用/禁用
     */
    public void setState(State newState);

    /**
     * 返回Group特有的KVStore视图
     */
    public KVStore getKVStore();

}
