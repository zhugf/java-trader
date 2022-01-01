package trader.service.event;

import trader.common.beans.ServiceStateAware;

public interface AsyncEventService extends ServiceStateAware {

    /**
     * 行情事件处理线程
     */
    public static final String FILTER_CHAIN_MD = "MD";

    /**
     * 交易事件处理线程
     */
    public static final String FILTER_CHAIN_TRADE = "Trade";

    /**
     * 增加事件处理过滤器. 相同Chain名称的Filter应该有相同的EventType High(高16字节).
     *
     * @param filterChainName
     * @param filter
     * @param eventMask
     */
    public void addFilter(String filterChainName, AsyncEventFilter filter, int eventMask);

    public long publishEvent(int eventType, AsyncEventProcessor processor, Object data, Object data2);

    public void publishProcessorEvent(AsyncEventProcessor processor, int dataType, Object data, Object data2);
}
