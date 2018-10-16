package trader.service.trade;

import java.util.LinkedList;
import java.util.List;

import trader.common.exchangeable.Exchangeable;

/**
 * 持仓的本地实现, 负责计算持仓盈亏等数据
 */
public class PositionImpl implements Position, TradeConstants {

    private Exchangeable exchangeable;
    private PosDirection direction;
    private long[] money = new long[PosMoney_Count];
    private int[] volumes = new int[PosVolume_Count];

    /**
     * 当前在途报单
     */
    private List<OrderImpl> orders = new LinkedList<>();

    public PositionImpl(Exchangeable e, PosDirection direction, long[] money, int[] volumes) {
        this.exchangeable = e;
        this.direction = direction;
        this.money = money;
        this.volumes = volumes;
    }

    public PositionImpl(Exchangeable e) {
        this.exchangeable = e;
        direction = PosDirection.Net;
    }

    @Override
    public Exchangeable getExchangeable() {
        return exchangeable;
    }

    @Override
    public PosDirection getDirection() {
        return direction;
    }

    @Override
    public long getMoney(int posMoneyIdx) {
        return money[posMoneyIdx];
    }

    @Override
    public int getVolume(int posVolumeIdx) {
        return volumes[posVolumeIdx];
    }

    @Override
    public List<Order> getActiveOrders() {
        return (List)orders;
    }

    public long addMoney(int posMoneyIdx, long toadd) {
        money[posMoneyIdx] += toadd;
        return money[posMoneyIdx];
    }

    public int addVolume(int posVolumeIdx, int toadd) {
        volumes[posVolumeIdx] += toadd;
        return volumes[posVolumeIdx];
    }

    /**
     * 本地计算和冻结仓位. 非线程安全
     */
    public void localFreeze(OrderImpl order) {
        localFreeze0(order, 1);
    }

    /**
     * 本地计算和解冻仓位, 非线程安全
     */
    public void localUnfreeze(OrderImpl order) {
        localFreeze0(order, -1);
    }

    private void localFreeze0(OrderImpl order, int unit) {
        if ( order.getOffsetFlags()==OrderOffsetFlag.OPEN ) {
            //开仓冻结资金
            long orderMarginReq = order.getMoney(OdrMoney_LocalFrozenMargin);
            long orderCommission = order.getMoney(OdrMoney_LocalFrozenCommission);

            addMoney(PosMoney_FrozenMargin, unit*orderMarginReq);
            addMoney(PosMoney_FrozenCommission, unit*orderCommission);
            if ( order.getDirection()==OrderDirection.Buy ) {
                addMoney(PosMoney_LongFrozenAmount, unit*orderMarginReq);
            }else{
                addMoney(PosMoney_ShortFrozenAmount, unit*orderMarginReq);
            }
        } else {
            //平仓冻结已有仓位
            int odrVol = order.getVolume(OdrVolume_ReqVolume);
            if ( order.getDirection()==OrderDirection.Sell ) {
                //多仓冻结
                addVolume(PosVolume_LongFrozen, unit*odrVol);
            }else {
                addVolume(PosVolume_ShortFrozen, unit*odrVol);
            }
        }
    }

}
