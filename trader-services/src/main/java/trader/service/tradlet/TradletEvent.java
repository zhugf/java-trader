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


    public static final int EVENT_TYPE_MD_TICK              = EVENT_CAT_MD|0X0001;
    public static final int EVENT_TYPE_MD_BAR               = EVENT_CAT_MD|0X0002;

    public static final int EVENT_TYPE_MISC_GROUP_UPDATE    = EVENT_CAT_MISC|0X0001;

    public int eventType;

    public Object data;

    public TradletEvent() {
    }

    public void setEvent(int eventType, Object data) {
        this.eventType = eventType;
        this.data = data;
    }

    public void clear() {
        eventType=0;
        data = null;
    }
}
