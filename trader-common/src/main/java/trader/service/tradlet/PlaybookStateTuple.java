package trader.service.tradlet;

import trader.service.trade.Order;
import trader.service.tradlet.TradletConstants.PlaybookState;

/**
 * Playbook的状态转换元组信息, 只读
 */
public interface PlaybookStateTuple {

    public PlaybookState getState();

    public long getTimestamp();

    public Order getOrder();
}