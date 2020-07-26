package trader.service.tradlet;

import java.util.Collection;
import java.util.List;

import trader.common.exception.AppException;
import trader.common.exchangeable.Exchangeable;
import trader.service.trade.Order;

/**
 * Playbook以及关联的报单, 成交的管理接口.
 * <BR>每个TradletGroup有自己独立的PlaybookKeeper实例.
 */
public interface PlaybookKeeper {

    /**
     * 返回属于这个分组的所有报单.
     * <p>只返回当天报单, 历史报单不返回
     */
    public List<Order> getAllOrders();

    /**
     * 返回当前未成交报单
     */
    public Collection<Order> getPendingOrders();

    /**
     * 最后报单
     */
    public Order getLastOrder();

    /**
     * 最后活跃报单
     */
    public Order getLastPendingOrder();

    /**
     * 取消所有的待成交订单
     */
    public void cancelAllPendingOrders();

    /**
     * 所有的交易剧本.
     * <BR>注意:每次启动后, 会自动删除非本交易日, 且已结束的交易剧本
     */
    public Collection<Playbook> getAllPlaybooks();

    /**
     * 返回活动的交易剧本列表
     */
    public List<Playbook> getActivePlaybooks(Exchangeable instrument);

    /**
     * 返回指定Playbook
     */
    public Playbook getPlaybook(String playbookId);

    /**
     * 异步创建一个新的Playbook, 当前如果有活跃的Playbook, 会先强制关闭
     */
    public Playbook createPlaybook(Tradlet tradlet, PlaybookBuilder builder) throws AppException;

    /**
     * 明确关闭一个Playbook, 平掉所有持仓.
     * <BR>只有处于Opening/Open状态的Playbook才能平仓
     *
     * @return true 平仓请求成功, false 状态不对或不需要实际动作
     */
    public boolean closePlaybook(Playbook playbook, PlaybookCloseReq closeReq);

}
