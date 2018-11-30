package trader.service.tradlet;

import trader.service.md.MarketData;

/**
 * 用于跨线程分发的策略事件
 */
public class TradletEvent {
    /**
     * 行情数据事件类型
     */
    public static final int EVENT_CAT_MARKETDATA            = 0X00010000;
    /**
     * 报单/成交回报
     */
    public static final int EVENT_CAT_TRADE                 = 0X00030000;
    /**
     * 杂类: 重新加载配置等等
     */
    public static final int EVENT_CAT_MISC                  = 0X00050000;


    public static final int EVENT_TYPE_MARKETDATA           = EVENT_CAT_MARKETDATA|0X0001;
    public static final int EVENT_TYPE_NEWBAR               = EVENT_CAT_MARKETDATA|0X0002;

    public static final int EVENT_TYPE_GROUP_UPDATE         = EVENT_CAT_MISC|0X0001;
    public static final int EVENT_TYPE_TRADLET_RELOAD       = EVENT_CAT_MISC|0X0001;

    private int eventType;

    private Object data;

    public TradletEvent() {
    }

    public void setEvent(int eventType, MarketData marketData) {
        this.eventType = eventType;
        this.data = marketData;
    }

    public int getEventType() {
        return eventType;
    }

    /**
     * 行情切片数据
     */
    public MarketData getAsMarketData() {
        return (MarketData)data;
    }

}
