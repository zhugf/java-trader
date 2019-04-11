package trader.service.tradlet;

/**
 * 用于跨线程分发的策略事件
 */
public class TradletEvent {
    /**
     * 行情数据事件类型
     */
    public static final int EVENT_CAT_MD                    = 0X00010000;
    /**
     * 报单/成交回报
     */
    public static final int EVENT_CAT_TRADE                 = 0X00030000;
    /**
     * 杂类: 重新加载配置等等
     */
    public static final int EVENT_CAT_MISC                  = 0X00050000;

    /**
     * 行情切片事件
     */
    public static final int EVENT_TYPE_MD_TICK              = EVENT_CAT_MD|0X0001;
    /**
     * 行情KBar事件
     */
    public static final int EVENT_TYPE_MD_BAR               = EVENT_CAT_MD|0X0002;
    /**
     * 交易报单回报事件
     */
    public static final int EVENT_TYPE_TRADE_ORDER          = EVENT_CAT_TRADE|0X0001;
    /**
     * 交易成交回报事件
     */
    public static final int EVENT_TYPE_TRADE_TXN            = EVENT_CAT_TRADE|0X0002;
    /**
     * 策略组重新加载事件
     */
    public static final int EVENT_TYPE_MISC_GROUP_UPDATE    = EVENT_CAT_MISC|0X0001;
    /**
     * 空闲超时事件
     */
    public static final int EVENT_TYPE_MISC_NOOP            = EVENT_CAT_MISC|0X0002;

    /**
     * 无行情数据超时发送NOOP Event的时间间隔
     */
    public static final int NOOP_TIMEOUT = 800;

    public int eventType;

    public Object data;

    public Object data2;

    public TradletEvent() {
    }

    public void setEvent(int eventType, Object data) {
        this.eventType = eventType;
        this.data = data;
    }

    public void setEvent(int eventType, Object data, Object data2) {
        this.eventType = eventType;
        this.data = data;
        this.data2 = data2;
    }

    public void clear() {
        eventType=0;
        data = null;
        data2 = null;
    }
}
