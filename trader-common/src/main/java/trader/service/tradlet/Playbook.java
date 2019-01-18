package trader.service.tradlet;

import java.util.List;

import trader.service.trade.Order;
import trader.service.trade.TradeConstants.PosDirection;

/**
 * 交易剧本, 包含了一次量化开平仓的所有细节, 一个交易剧本实例对象不允许同时持有多空仓位.
 * <BR>开仓价格,理由, 止损, 止盈, 最长持有时间等等
 */
public interface Playbook extends TradletConstants {

    /**
     * Order的属性, 用于关联Order与Playbook
     */
    public static final String ATTR_PLAYBOOK_ID = "playbookId";

    /**
     * 全局唯一ID
     */
    public String getId();

    /**
     * 剧本模板ID
     */
    public String getTemplateId();

    /**
     * 所有的历史状态
     */
    public List<PlaybookStateTuple> getStateTuples();

    /**
     * 当前状态
     */
    public PlaybookStateTuple getStateTuple();

    /**
     * 剧本参数, 缺省值可以从配置参数填充
     */
    public Object getAttr(String attr);

    /**
     * 当前持仓手数
     */
    public int getVolume();

    /**
     * 返回平均成交价格
     */
    public long getPrice();

    /**
     * 持仓方向
     */
    public PosDirection getDirection();

    /**
     * 关联的所有报单
     */
    public List<Order> getOrders();

    /**
     * 当前待成交订单
     */
    public Order getPendingOrder();
}