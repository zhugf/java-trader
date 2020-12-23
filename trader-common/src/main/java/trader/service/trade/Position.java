package trader.service.trade;

import java.util.Collection;

import trader.common.exchangeable.Exchangeable;
import trader.common.util.JsonEnabled;
import trader.service.trade.TradeConstants.PosDirection;
import trader.service.trade.TradeConstants.PosMoney;
import trader.service.trade.TradeConstants.PosVolume;

/**
 * 持仓
 */
public interface Position extends JsonEnabled {

    public Account getAccount();

    public Exchangeable getInstrument();

    public PosDirection getDirection();

    public long[] getMoneys();

    /**
     */
    public long getMoney(PosMoney mny);

    /**
     */
    public int getVolume(PosVolume vol);

    /**
     * 关联的活跃报单
     */
    public Collection<Order> getActiveOrders();

    /**
     * 持仓明细
     */
    public Collection<PositionDetail> getDetails();

}
