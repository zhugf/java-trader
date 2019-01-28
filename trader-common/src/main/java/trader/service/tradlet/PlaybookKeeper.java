package trader.service.tradlet;

import java.util.Collection;
import java.util.List;

import trader.common.exception.AppException;
import trader.service.trade.Order;

/**
 * Playbook以及关联的报单, 成交的管理接口
 */
public interface PlaybookKeeper {

    /**
     * 返回属于这个分组的所有报单
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
    public Collection<Playbook> getAllPlaybooks();

    /**
     * 返回活动的交易剧本
     * @param tradletId tradlet Id, null代表返回所有活动playbook
     */
    public Collection<Playbook> getActivePlaybooks(String tradletId);

    /**
     * 返回指定Playbook
     */
    public Playbook getPlaybook(String playbookId);

    /**
     * 异步创建一个新的Playbook, 当前如果有活跃的Playbook, 会先强制关闭
     */
    public void createPlaybook(PlaybookBuilder builder) throws AppException;

    /**
     * 明确关闭一个Playbook, 平掉所有持仓.
     * <BR>只有处于Opening/Open状态的Playbook才能平仓
     */
    public boolean closePlaybook(Playbook playbook, PlaybookCloseReq closeReq);

}
