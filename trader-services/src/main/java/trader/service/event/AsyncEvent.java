package trader.service.event;

/**
 * 异步处理事件, 为RingBuffer服务
 */
public class AsyncEvent {
    /**
     * 事件类型掩码
     */
    public static final int EVENT_TYPE_MASK         = 0XFFFF0000;
    /**
     * 行情数据事件类型
     */
    public static final int EVENT_TYPE_MARKETDATA   = 0X00010000;
    /**
     * 通过调用process来干活, 低16BIT是Processor自用的数据类型
     */
    public static final int EVENT_TYPE_PROCESSOR    = 0X00020000;

    /**
     * 事件类型, TYPE_PROCESSOR需要调用processor处理数据
     */
	public int eventType;

	/**
	 * 事件处理句柄
	 */
	public AsyncEventProcessor processor;

	/**
	 * 数据
	 */
	public Object data;

	public Object data2;

	public void setData(int eventType, Object data) {
	    this.eventType = eventType;
	    this.data = data;
	    this.data2 = null;
	}

    public void setData(int eventType, Object data, Object data2) {
        this.eventType = eventType;
        this.data = data;
        this.data2 = data2;
    }

    public void clear() {
        eventType = 0;
        processor = null;
        data = null;
        data2 = null;
    }

}
