package trader.common.beans;

import java.util.concurrent.Callable;

public interface ServiceEventHub {

    /**
     * 当某个Service的状态发生改变时, 收到该消息
     */
    public static final String TOPIC_SERVICE_STATE_CHANGED = "serviceStateChanged";

    /**
     * 当全部Service初始化完毕后, 收到该消息
     */
    public static final String TOPIC_SERVICE_ALL_INIT_DONE = "serviceAllInitDone";

    public static final String ATTR_STATE = "state";
    public static final String ATTR_PUBLISHER = "publisher";

    public void addListener(ServiceEventListener listener, String...topics);

    public void publish(ServiceEvent event);

    /**
     * Service的异步初始函数, 当前置条件满足时才被调用. 从而实现并行初始化, 降低整体初始化时长.
     * @param serviceName TODO
     */
    public void registerServiceInitializer(String serviceName, Callable<ServiceStateAware> serviceInitializer, ServiceStateAware ...dependents);

}
