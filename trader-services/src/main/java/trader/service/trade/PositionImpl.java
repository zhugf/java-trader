package trader.service.trade;

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
    private List<PositionDetail> details;

    public PositionImpl(Exchangeable e,PosDirection direction, long[] money, int[] volumes) {
        this.exchangeable = e;
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
    public List<PositionDetail> getDetails() {
        return details;
    }

}
