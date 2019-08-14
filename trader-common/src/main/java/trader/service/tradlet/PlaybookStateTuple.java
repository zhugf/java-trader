package trader.service.tradlet;

import java.time.LocalDate;

import trader.service.trade.Order;
import trader.service.trade.TradeConstants.OrderAction;
import trader.service.tradlet.TradletConstants.PlaybookState;

/**
 * Playbook的状态转换元组信息, 只读
 */
public interface PlaybookStateTuple {
    /**
     * 状态
     */
    public PlaybookState getState();

    /**
     * 时间戳
     */
    public long getTimestamp();

    /**
     * 交易日
     */
    public LocalDate getTradingDay();

    /**
     * 报单
     */
    public Order getOrder();

    /**
     * 报单ID
     */
    public String getOrderRef();

    /**
     * 报单动作
     */
    public OrderAction getOrderAction();

    /**
     * 动作Id
     */
    public String getActionId();
}