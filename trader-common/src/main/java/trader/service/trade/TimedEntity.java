package trader.service.trade;

import java.time.LocalDate;

import trader.common.beans.Identifiable;
import trader.common.exchangeable.Exchangeable;

/**
 * 作为一个时间关联实体
 */
public interface TimedEntity extends Identifiable{

    /**
     * 关联交易账户ID
     */
    public String getAccountId();

    /**
     * 交易日
     */
    public LocalDate getTradingDay();

    public Exchangeable getInstrument();
}
