package trader.service.ta;

import org.ta4j.core.TimeSeries;

import trader.common.tick.PriceLevel;

/**
 * 附带级别信息的K线数据
 */
public interface LeveledTimeSeries extends TimeSeries {

    PriceLevel getLevel();

}
