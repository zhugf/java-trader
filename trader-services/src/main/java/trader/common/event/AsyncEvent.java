package trader.common.event;

/**
 * 异步处理事件, 为RingBuffer服务
 */
public class AsyncEvent {
    /**
     * 通过调用process来干活
     */
    public static final int EVENT_TYPE_PROCESSOR = 0;
    /**
     * 行情数据
     */
    public static final int EVENT_TYPE_MARKETDATA = 1;

    /**
     * 事件类型, TYPE_PROCESSOR需要调用processor处理数据
     */
	public int eventType;
	/**
	 * 事件处理句柄
	 */
	public AsyncEventProcessor processor;
	/**
	 * 数据类型
	 */
	public int dataType;

	/**
	 * 数据
	 */
	public Object data;

	public Object data2;

	public Object data3;

	public void setData(int dataType, Object data) {
	    this.dataType = dataType;
	    this.data = data;
	    this.data2 = null;
	    this.data3 = null;
	}

    public void setData(int dataType, Object data, Object data2) {
        this.dataType = dataType;
        this.data = data;
        this.data2 = data2;
        this.data3 = null;
    }
}
