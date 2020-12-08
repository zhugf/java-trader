package trader.service.tradlet;

import java.util.Map;

import trader.service.md.MarketData;
import trader.service.ta.LeveledBarSeries;
import trader.service.trade.Order;
import trader.service.trade.Transaction;

/**
 * 交易策略实例, 可以动态加载和释放.
 * <BR>Tradlet实例必须属于某个TradletGroup关联, 在初始化时, 从BeansContainer.getBean(TradletGroup.class)方式拿到
 * <BR>
 * <BR>Tradlet与Playbook与PolicyId的关系
 * <LI>TradletGroup可以有多个Tradlet
 * <LI>Tradlet可以有多个各自的Action, 每个Action代表一个实际的开仓平仓动作原因. 格式为: tradletId.actionName, 这样从actionId可以反推tradletId
 * <P>一个Tradlet有多个Action的原因: 开仓/平仓策略很多是不同参数调整的结果. 如果每个Policy都对应个一个Tradlet实现类,
 * 那么就无法共享数据结构, 消耗过多CPU和内存.
 * <LI>TradletGroup可以有多个活动的Playbook实例
 * <LI>Playbook 在生命周期中, 会被TradletGroup的所有Tradlet关注, 但是只能有一个实际的平仓动作
 */
public interface Tradlet {

    /**
     * 初始化
     * <BR>如果Tradlet所在的Plugin有jar文件更新, 会创建新的Tradlet实例, 并重新init
     */
    public void init(TradletContext context) throws Exception;

    /**
     * 当有配置更新时, 重新加载.
     *
     * @param context 上下文, 如果tradlet被禁用, 值为null
     */
    public void reload(TradletContext context) throws Exception;

    /**
     * 销毁实例, 释放资源
     */
    public void destroy();

    /**
     * 交互式查询数据, 由TradletGroup REST Controller调用
     * @param path 查询路径
     * @param params 查询参数
     * @param payload 查询内容
     */
    public Object onRequest(String path, Map<String, String> params, String payload);

    /**
     * 当Playbook的状态发生变动
     *
     * @param oldStateTuple null代表新建Playbook
     */
    public void onPlaybookStateChanged(Playbook playbook, PlaybookStateTuple oldStateTuple);

    /**
     * 当有新的行情切片来的时候
     */
    public void onTick(MarketData tick);

    /**
     * 当有新的分钟线产生, 这个函数在新的Bar所在的Tick后调用
     */
    public void onNewBar(LeveledBarSeries series);

    /**
     * 当有新的成交时
     */
    public void onTransaction(Order order, Transaction txn);

    /**
     * 当新的一秒来到时, 如果上一秒没有行情数据, 会主动调用这个函数.
     * <BR>当中场休息或不活跃合约时, 这个函数会被调用
     */
    public void onNoopSecond();

}
