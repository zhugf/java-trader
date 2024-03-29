package trader.service.tradlet;

import java.util.List;

import trader.common.beans.Identifiable;
import trader.common.exception.AppException;
import trader.common.exchangeable.Exchangeable;
import trader.service.trade.Order;
import trader.service.trade.TimedEntity;
import trader.service.trade.TradeConstants.PosDirection;

/**
 * 交易剧本, 包含了一次量化开平仓回合的所有细节, 一个交易剧本实例对象不允许同时持有多空仓位.
 * <BR>开仓价格,理由, 止损, 止盈, 最长持有时间等等
 *
 * <LI>Playbook实例可以存储和恢复, 用于处理隔夜持仓和交易程序的重启
 * <LI>Playbook实例在创建时, 可以指定模板, 并给定参数替换, 用于实现止盈止损的具体方式.
 */
public interface Playbook extends TimedEntity, TradletConstants {

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
    public Object getAttr(String attr, Object defaultv);

    /**
     * 动态设置属性
     */
    public void setAttr(String attr, Object value);

    /**
     * 属性版本, 属性变化时, 版本+1
     */
    public int getVersion();

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
    public long getMoney(PBMoney mny);

    /**
     * 持仓方向, 平仓方向成为Net
     */
    public PosDirection getDirection();

    /**
     * 关联的所有报单
     */
    public List<Order> getOrders();

    /**
     * 关联的全部报单ID
     */
    public List<String> getOrderIds() ;

    /**
     * 当前待成交订单
     */
    public Order getPendingOrder();

    /**
     * 返回持仓时间, 从开仓报单成交算起
     */
    public long getPositionTime();

    /**
     * 实际开仓, 应该在创建之后, 尽快调用.
     */
    public void open() throws AppException;

}