package trader.service.tradlet;

import java.util.List;
import java.util.Map;

import trader.common.exchangeable.Exchangeable;
import trader.common.util.JsonEnabled;
import trader.service.trade.Account;

/**
 * 交易策略分组, 一组开平仓策略以及相关配置参数构成一个完整的交易模型, 可以附加一些额外的简单逻辑控制和事件回调函数. 支持动态修改
 * <BR>自身保存一些tradlet之间共享的数据, 例如当前活跃订单等等.
 * <BR>每个分组, 运行在独立的线程中
 */
public interface TradletGroup extends TradletConstants, JsonEnabled {

    /**
     * 分组ID
     */
    public String getId();

    /**
     * 账户视图
     */
    public Account getAccount();

    /**
     * 关注的交易品种
     */
    public List<Exchangeable> getInstruments();

    /**
     * 返回指定的交易小程序
     */
    public Tradlet getTradlet(String tradletId);

    /**
     * 交易程序列表.
     * <BR>注意該函數在Tradlet.init()中无法运用
     */
    public List<Tradlet> getTradlets();

    /**
     * 获取配置状态
     */
    public TradletGroupState getConfigState();

    /**
     * 当前状态
     */
    public TradletGroupState getState();

    /**
     * 设置启用/禁用
     */
    public void setState(TradletGroupState newState);

    /**
     * 返回Playbook管理类
     */
    public PlaybookKeeper getPlaybookKeeper();

    /**
     * 交互式查询数据
     */
    public String onRequest(String path, String payload, Map<String, String> params);
}