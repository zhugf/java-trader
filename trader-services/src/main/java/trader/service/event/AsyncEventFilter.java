package trader.service.event;

/**
 * 注册异步事件处理过滤器
 */
public interface AsyncEventFilter {

    /**
     * 处理事件
     *
     * @return true表示后续filter可以继续处理, false表示后续filter不应该继续处理
     */
    boolean onEvent(AsyncEvent event);

}
