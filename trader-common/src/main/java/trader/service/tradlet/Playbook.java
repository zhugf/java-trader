package trader.service.tradlet;

import java.util.List;

import trader.common.exchangeable.Exchangeable;
import trader.service.trade.Order;
import trader.service.trade.TradeConstants.PosDirection;

/**
 * 交易剧本, 包含了一次量化开平仓回合的所有细节, 一个交易剧本实例对象不允许同时持有多空仓位.
 * <BR>开仓价格,理由, 止损, 止盈, 最长持有时间等等
 *
 * <BR>一个Playbook可以存储和恢复, 用于处理隔夜持仓和交易程序的重启
 */
public interface Playbook extends TradletConstants {

    public static final String PBACTION_TIMEOUT = "pbActionTimeout";

    /**
     * 开仓超时(毫秒), 超时后会主动撤销, 修改状态为Canceling.
     * <BR>0表示不自动超时
     */
    public static final String PBATR_OPEN_TIMEOUT = "openTimeout";
    /**
     * 平仓超时(毫秒), 超时后会自动修改为现价成交, 修改状态为ForceClosing
     * <BR>0表示不自动强制平仓
     */
    public static final String PBATR_CLOSE_TIMEOUT = "closeTimeout";

    public static final String DEFAULT_OPEN_TIMEOUT = "5s";
    public static final String DEFAULT_CLOSE_TIMEOUT = "5s";

    /**
     * 当前App唯一ID, 每节点每App唯一.
     * 格式为: pb_yyyymmddhhmmssSSS[00], 例如: pb_2019081410033719800
     */
    public String getId();

    public Exchangeable getInstrument();

    /**
     * 所有的历史状态
     */
    public List<PlaybookStateTuple> getStateTuples();

    /**
     * 根据状态找到对应的状态元组
     *
     * @param state 预期的state, null代表第一个状态
     */
    public PlaybookStateTuple getStateTuple(PlaybookState state);

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
    public int getVolume(PBVol volIndex);

    /**
     * 返回平均成交价格和持仓利
     * <p>在跨日持仓结算和持仓明细的先开先平影响下, 这个值和Position.getMoney返回值可以不一致.
     */
    public long getMoney(PBMny mny);

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