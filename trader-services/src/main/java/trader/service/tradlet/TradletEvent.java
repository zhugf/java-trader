package trader.service.tradlet;

import java.util.Collection;
import java.util.Map;

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

    private EventType eventType;

    private MarketData marketData;

    private Object eventData;

    public TradletEvent() {
    }

    public void setEvent(EventType eventType, MarketData marketData) {
        this.eventType = eventType;
        this.marketData = marketData;
        this.eventData = null;
    }

    public void setEvent(EventType eventType, Object eventData) {
        this.eventType = eventType;
        this.marketData = null;
        this.eventData = eventData;
    }

    public EventType getEventType() {
        return eventType;
    }

    /**
     * 行情切片数据
     */
    public MarketData getMarketData() {
        return marketData;
    }

    /**
     * 新的KBar
     */
    public LeveledTimeSeries getLeveldTimeSeries() {
        return (LeveledTimeSeries)eventData;
    }

    /**
     * 更新的策略组配置
     */
    public Map getGroupConfig() {
        return (Map)eventData;
    }

    /**
     * 更新的交易策略ID列表
     */
    public Collection<String> getTradletIds(){
        return (Collection)eventData;
    }

}
