package trader.service.trade;

import trader.service.trade.TradeConstants.PosDirection;

/**
 * 持仓明细
 */
public interface PositionDetail {

    public PosDirection getDirection();

    public Order getOrder();

}
