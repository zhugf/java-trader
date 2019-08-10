package trader.service.tradlet;

import java.util.List;

import trader.common.exchangeable.Exchangeable;
import trader.service.trade.Order;
import trader.service.trade.TradeConstants.PosDirection;

/**
 * 交易剧本, 包含了一次量化开平仓回合的所有细节, 一个交易剧本实例对象不允许同时持有多空仓位.
 * <BR>开仓价格,理由, 止损, 止盈, 最长持有时间等等
 */
public interface Playbook extends TradletConstants {

    public static final String ACTION_ID_TIMEOUT = "pbTimeout";

    /**
     * 开仓超时(毫秒), 超时后会主动撤销, 修改状态为Canceling.
     * <BR>0表示不自动超时
     */
    public static final String ATTR_OPEN_TIMEOUT = "openTimeout";
    /**
     * 平仓超时(毫秒), 超时后会自动修改为现价成交, 修改状态为ForceClosing
     * <BR>0表示不自动强制平仓
     */
    public static final String ATTR_CLOSE_TIMEOUT = "closeTimeout";

    public static final int DEFAULT_OPEN_TIMEOUT = 5000;
    public static final int DEFAULT_CLOSE_TIMEOUT = 5000;

    /**
     * 全局唯一ID
     */
    public String getId();

    public Exchangeable getExchangable();

    /**
     * 所有的历史状态
     */
    public List<PlaybookStateTuple> getStateTuples();

    /**
     * 当前状态
     */
    public PlaybookStateTuple getStateTuple();

    /**
     * 剧本属性, 缺省值可以从配置参数填充
     */
    public Object getAttr(String attr);

    /**
     * 动态设置属性
     */
    public void setAttr(String attr, Object value);

    /**
     * 当前持仓手数
     * @see TradletConstants#PBVol_Opening
     * @see TradletConstants#PBVol_Open
     * @see TradletConstants#PBVol_Closeing
     * @see TradletConstants#PBVol_Close
     * @see TradletConstants#PBVol_Pos
     */
    public int getVolume(int volIndex);

    /**
     * 返回平均成交价格
     *
     * @see TradletConstants#PBMny_Open
     * @see TradletConstants#PBMny_Close
     */
    public long getMoney(int mnyIndex);

    /**
     * 持仓方向, 平仓方向成为Net
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