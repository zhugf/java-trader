package trader.service.trade;

import java.util.List;

import trader.common.exchangeable.Exchangeable;
import trader.service.trade.TradeConstants.PosDirection;

/**
 * 持仓
 */
public interface Position {

    public Exchangeable getExchangeable();

    public PosDirection getDirection();

    /**
     * @see TradeConstants#PosMoney_LongFrozenAmount
     * @see TradeConstants#PosMoney_ShortFrozenAmount
     * @see TradeConstants#PosMoney_OpenAmount
     * @see TradeConstants#PosMoney_CloseAmount
     * @see TradeConstants#PosMoney_OpenCost
     * @see TradeConstants#PosMoney_PositionCost
     * @see TradeConstants#PosMoney_PreMargin
     * @see TradeConstants#PosMoney_UseMargin
     * @see TradeConstants#PosMoney_FrozenMargin
     * @see TradeConstants#PosMoney_FrozenCommission
     * @see TradeConstants#PosMoney_Commission
     * @see TradeConstants#PosMoney_CloseProfit
     * @see TradeConstants#PosMoney_PositionProfit
     * @see TradeConstants#PosMoney_PreSettlementPrice
     * @see TradeConstants#PosMoney_SettlementPrice
     * @see TradeConstants#PosMoney_ExchangeMargin
     * @see TradeConstants#PosMoney_FrozenCash
     * @see TradeConstants#PosMoney_CashIn
     */
    public long getMoney(int posMoneyIdx);

    /**
     * @see TradeConstants#PosVolume_Position
     * @see TradeConstants#PosVolume_OpenVolume
     * @see TradeConstants#PosVolume_CloseVolume
     * @see TradeConstants#PosVolume_LongFrozen
     * @see TradeConstants#PosVolume_ShortFrozen
     * @see TradeConstants#PosVolume_FrozenPosition
     * @see TradeConstants#PosVolume_TodayPosition
     * @see TradeConstants#PosVolume_YdPosition
     */
    public int getVolume(int posVolumeIdx);

    public List<PositionDetail> getDetails();
}
