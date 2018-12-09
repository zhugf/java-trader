package trader.service.event;

/**
 * 异步处理事件, 为RingBuffer服务
 */
public class AsyncEvent {
    /**
     * 行情数据事件类型
     */
    public static final int EVENT_TYPE_MARKETDATA           = 0X00010000;
    public static final int EVENT_TYPE_MARKETDATA_MASK      = 0X0000FFFF|EVENT_TYPE_MARKETDATA;
    /**
     * 通过调用process来干活, 低16BIT是Processor自用的数据类型
     */
    public static final int EVENT_TYPE_PROCESSOR            = 0X00020000;
    public static final int EVENT_TYPE_PROCESSOR_MASK       = 0X0000FFFF|EVENT_TYPE_PROCESSOR;

    /**
     * 事件类型, 高16BIT是事件类型, 低16BIT是数据类型(可选, 缺省为0)
     */
	public int eventType;

	/**
	 * 事件处理句柄. 当事件类型为EVENT_TYPE_PROCESSOR时起作用
	 */
	public AsyncEventProcessor processor;

	/**
	 * 数据
	 */
	public Object data;

	public Object data2;

    public void setData(int eventType, AsyncEventProcessor processor, Object data, Object data2) {
        this.eventType = eventType;
        this.processor = processor;
        this.data = data;
        this.data2 = data2;
    }

    void clear() {
        eventType = 0;
        processor = null;
        data = null;
        data2 = null;
    }

}
