package trader.service.event;

/**
 * 注册异步事件处理过滤器
 */
public interface AsyncEventFilter {

    /**
     * 处理事件
     * @return true表示event已经被处理, false表示未处理
     */
    boolean onEvent(AsyncEvent event);

}
