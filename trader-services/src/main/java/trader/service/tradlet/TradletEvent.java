package trader.service.tradlet;

import trader.service.md.MarketData;
import trader.service.ta.LeveledTimeSeries;

/**
 * 用于跨线程分发的策略事件
 */
public class TradletEvent {
    public static enum EventType{
        /**
         * 新的行情切片
         */
        MarketData
        /**
         * 新的KBar
         */
        ,Bar
        /**
         * 报单状态
         */
        ,Order
        /**
         * 成交
         */
        ,Transaction
        /**
         * 策略实现类更新
         */
        ,TradletReloaded
        /**
         * 策略分组配置更新
         */
        ,TradletGroupUpdated
    };

    public EventType eventType;

    public MarketData marketData;

    public LeveledTimeSeries leveledTimeSeries;

}
