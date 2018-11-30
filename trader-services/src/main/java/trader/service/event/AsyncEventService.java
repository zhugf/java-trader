package trader.service.event;

import trader.service.md.MarketData;

public interface AsyncEventService {

    /**
     * 行情数据保存专用
     */
    public static final String FILTER_CHAIN_MARKETDATA_SAVER = "marketDataSaver";

    /**
     * 主事件处理线程
     */
    public static final String FILTER_CHAIN_MAIN = "Main";

    /**
     * 增加事件处理过滤器, 不同名称的过滤器会放在不同的EventHandler中执行
     *
     * @param filterChainName
     * @param filter
     */
    public void addFilter(String filterChainName, AsyncEventFilter filter, int eventMask);

    public void publishMarketData(MarketData md);

    public void publishProcessorEvent(AsyncEventProcessor processor, int dataType, Object data, Object data2);
}
