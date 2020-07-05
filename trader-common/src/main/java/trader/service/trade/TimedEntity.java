package trader.service.trade;

import java.time.LocalDate;

import trader.common.exchangeable.Exchangeable;

/**
 * 作为一个时间关联实体
 */
public interface TimedEntity {

    /**
     * 唯一ID
     */
    public String getId();

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
