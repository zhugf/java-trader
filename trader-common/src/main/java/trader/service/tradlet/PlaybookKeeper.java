package trader.service.tradlet;

import java.util.List;

import trader.common.exception.AppException;
import trader.service.trade.Order;

/**
 * Playbook以及关联的报单, 成交的管理接口.
 * <BR>每个TradletGroup有自己独立的PlaybookKeeper实例.
 */
public interface PlaybookKeeper {

    /**
     * 返回属于这个分组的所有报单.
     * <p>只返回报单当天报单, 历史报单不返回
     */
    public List<Order> getAllOrders();

    /**
     * 返回当前未成交报单
     */
    public List<Order> getPendingOrders();

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
     * 所有的交易剧本
     */
    public List<Playbook> getAllPlaybooks();

    /**
     * 返回活动的交易剧本列表
     *
     * @param query expr 查询语句, cqengine语法
     */
    public List<Playbook> getActivePlaybooks(String queryExpr);

    /**
     * 返回指定Playbook
     */
    public Playbook getPlaybook(String playbookId);

    /**
     * 异步创建一个新的Playbook, 当前如果有活跃的Playbook, 会先强制关闭
     * @param tradlet TODO
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
