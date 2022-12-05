package trader.service.beans;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Primary;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import trader.common.beans.BeansContainer;
import trader.common.beans.ServiceEvent;
import trader.common.beans.ServiceEventHub;
import trader.common.beans.ServiceEventListener;
import trader.common.beans.ServiceState;
import trader.common.beans.ServiceStateAware;
import trader.common.util.concurrent.DelegateExecutor;

@Primary
@Service
public class BeansContainerImpl implements BeansContainer, ServiceEventHub {
    private final static Logger logger = LoggerFactory.getLogger(BeansContainerImpl.class);

    /**
     * 系统初始化最大 5 分钟, 超时则强制初始化
     */
    private static final int SERVICE_INIT_MAX_TIME = 5*60*1000;


    private static class ServiceInitItem {
        final String name;
        final Callable<ServiceStateAware> initializer;
        private ServiceStateAware svcState;
        final List<ServiceStateAware> dependents;
        private volatile ServiceState initState;
        public int index=0;

        ServiceInitItem(String name, Callable<ServiceStateAware> initializer, ServiceStateAware[] dependents){
            this.name = name;
            this.initializer = initializer;
            List<ServiceStateAware> dependents0 = new ArrayList<>();
            if ( null!=dependents) {
                for(ServiceStateAware d:dependents) {
                    if ( null!=d ) {
                        dependents0.add(d);
                    }
                }
            }
            this.dependents = dependents0;
            initState = ServiceState.NotInited;
        }

        public String toString() {
            return name+" "+initState;
        }

        public void setState(ServiceState state) {
            this.initState = state;
        }

        public ServiceState getState() {
            ServiceState result = initState;
            if ( null!=svcState ) {
                result = svcState.getState();
            }
            return result;
        }
    }

    @Autowired
    private ApplicationContext appContext;

    @Autowired
    private ExecutorService executorService;

    private Map<String, List<ServiceEventListener>> listenerByTopics = new HashMap<>();

    private DelegateExecutor eventPublishExecutor;

    private ServiceState state = ServiceState.NotInited;

    private ReentrantLock svcInitLock = new ReentrantLock();
    private List<ServiceInitItem> svcInitItems = Collections.synchronizedList(new ArrayList<>());
    private AtomicInteger svcInitIdx = new AtomicInteger();
    private volatile long svcInitThreadId = 0;
    /**
     * Spring Service 全部创建完成时间
     */
    private volatile long svcAllConstructTime=0;

    @PostConstruct
    public void init() {
        state = ServiceState.Starting;
        //启动初始化线程
        eventPublishExecutor = new DelegateExecutor(executorService, 1);
    }

    @PreDestroy
    public void destroy() {
        state = ServiceState.Stopped;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReadyEvent(ApplicationReadyEvent event) {
        svcAllConstructTime = System.currentTimeMillis();
    }

    public Collection<String> getBeanNames(){
        return Arrays.asList( appContext.getBeanNamesForType((Class)null) );
    }

    public<T> T getBean(String name) {
        try {
            return (T)appContext.getBean(name);
        }catch(Throwable t) {}
        return null;
    }

    @Override
    public <T> T getBean(Class<T> clazz) {
        try {
            return appContext.getBean(clazz);
        }catch(Throwable t) {}
        return null;
    }

    @Override
    public <T> T getBean(Class<T> clazz, String purposeOrId) {
        try {
            return (T)appContext.getBean(purposeOrId);
        }catch(Throwable t) {}
        return null;
    }

    @Override
    public <T> Map<String, T> getBeansOfType(Class<T> clazz) {
        try{
            return appContext.getBeansOfType(clazz);
        }catch(Throwable t) {}
        return Collections.EMPTY_MAP;
    }

    @Override
    public synchronized void addListener(ServiceEventListener listener, String... topics) {
        for(String topic:topics) {
            List<ServiceEventListener> listeners = listenerByTopics.get(topic);
            if ( null==listeners ) {
                listeners = new ArrayList<>();
                listenerByTopics.put(topic, listeners);
            }
            listeners.add(listener);
        }
    }

    @Override
    public synchronized void publish(ServiceEvent event) {
        List<ServiceEventListener> listeners = listenerByTopics.get(event.getTopic());
        if (null!=listeners) {
            eventPublishExecutor.execute(()->{
                for(ServiceEventListener listener:listeners) {
                    try{
                        listener.onEvent(event);
                    }catch(Throwable t) {
                        logger.error("Service event listener "+listener+" on event topic "+event.getTopic()+" failed");
                    }
                }
            });
        }
    }

    public void registerServiceInitializer(String svcName, Callable<ServiceStateAware> svcInitializer, ServiceStateAware ...dependents) {
        svcInitAddItem(svcName, svcInitializer, dependents);
    }

    private void svcInitAddItem(String svcName, Callable<ServiceStateAware> svcInitializer, ServiceStateAware[] dependents) {
        svcInitLock.lock();
        try {
            ServiceInitItem item = new ServiceInitItem(svcName, svcInitializer, dependents);
            svcInitItems.add(item);
            if (0==svcInitThreadId) {
                svcInitThreadId = 1;
                executorService.execute(()->{
                    svcInitThreadId = Thread.currentThread().getId();
                    try{
                        svcInitThreadFunc();
                    }catch(Throwable t) {
                        logger.error("Service init thread exception", t);
                    }
                });
            }
        }finally {
            svcInitLock.unlock();
        }
    }

    private void svcInitThreadFunc() {
        logger.info("Service init thread is running");
        long beginTime = System.currentTimeMillis();
        boolean forceInitAll=false;
        while(this.state!=ServiceState.Stopped ) {
            //全部Service启动完毕后计时, 超时则强制初始化
            if ( 0!=svcAllConstructTime && ((System.currentTimeMillis()-svcAllConstructTime)>=SERVICE_INIT_MAX_TIME) ){
                forceInitAll = true;
                break;
            }
            ServiceInitItem item = svcInitNextItem();
            if ( null!=item ) {
                svcInitItemPerform(item);
                continue;
            }
            //需要等到Spring 初始化完毕才能退出, 避免有一些Bean register太慢导致的问题
            if ( svcInitItemsAllDone() && svcAllConstructTime!=0 ) {
                break;
            }
            try{
                Thread.sleep(10);
            }catch(Throwable t) {}
        }
        if ( forceInitAll ) {
            //超时则强制初始化
            for(ServiceInitItem item: new ArrayList<>(svcInitItems) ) {
                if (item.initState==ServiceState.NotInited) {
                    logger.warn("Service "+item.name+" force init");
                    svcInitItemPerform(item);
                }
            }
        }

        //检查初始化完成状态, 结束后发送
        boolean serviceAllInitDone=false;
        long checkTime = System.currentTimeMillis();
        while( (System.currentTimeMillis()-checkTime)<=SERVICE_INIT_MAX_TIME ) {
            boolean allServiceDone=true;
            for(ServiceInitItem item: new ArrayList<>(svcInitItems) ) {
                if (!item.getState().isDone()) {
                    allServiceDone=false;
                    break;
                }
            }
            if ( allServiceDone ) {
                serviceAllInitDone = true;
                break;
            }
        }
        List<Object> pendingServices = new ArrayList<>();
        if ( !serviceAllInitDone ) {
            for(ServiceInitItem item: new ArrayList<>(svcInitItems) ) {
                if ( !item.getState().isDone() ) {
                    pendingServices.add(item.initializer);
                }
            }
        }

        publish(new ServiceEvent(TOPIC_SERVICE_ALL_INIT_DONE, this, new Object[] {}));
        if ( pendingServices.isEmpty() ) {
            logger.info("Total "+svcInitItems.size()+" services initialized in "+(System.currentTimeMillis()-beginTime)+" ms");
        } else {
            logger.info("Total "+svcInitItems.size()+" services initialized in "+(System.currentTimeMillis()-beginTime)+" ms, "+pendingServices.size()+" services are not ready yet: "+pendingServices);
        }
    }

    /**
     * 异步执行初始化动作
     */
    private void svcInitItemPerform(ServiceInitItem item) {
        item.index = svcInitIdx.getAndIncrement();
        item.setState( ServiceState.Starting);
        executorService.execute(()->{
            logger.debug("Service "+item.name+" begin to init");
            long bt=System.currentTimeMillis();
            ServiceStateAware aware = null;
            try{
                item.svcState = item.initializer.call();
                item.setState(ServiceState.Ready);
                long et=System.currentTimeMillis();
                logger.debug("Service "+item.name+" init in "+(et-bt)+" ms");
            }catch(Throwable t) {
                logger.error(item.initializer.toString()+" init failed", t);
                item.initState = ServiceState.Stopped;
            }
            if ( null!=aware ) {
                //publish service event
                publish(new ServiceEvent(TOPIC_SERVICE_STATE_CHANGED, aware, new Object[] {item.initState}));
            }
        });
    }

    /**
     * 找到下一个可以初始化的动作
     */
    private ServiceInitItem svcInitNextItem() {
        ServiceInitItem result = null;

        svcInitLock.lock();
        try {
            for(ServiceInitItem item:svcInitItems) {
                if ( ServiceState.NotInited!=item.getState() ) {
                    continue;
                }
                boolean dependentsAllReady = true;
                if ( item.dependents.size()>0 ) {
                    for(ServiceStateAware dependent:item.dependents) {
                        //需要每个依赖服务的状态 Ready或Stopped
                        if ( null!=dependent && !dependent.getState().isDone() ) {
                            dependentsAllReady = false;
                            break;
                        }
                    }
                }
                if ( dependentsAllReady ) {
                    result = item;
                    break;
                }
            }
        }finally {
            svcInitLock.unlock();
        }
        return result;
    }

    private boolean svcInitItemsAllDone() {
        boolean result=true;
        svcInitLock.lock();
        try {
            for(ServiceInitItem item: (svcInitItems) ) {
                if ( !item.initState.isDone() ) {
                    result=false;
                    break;
                }
            }
        }finally {
            svcInitLock.unlock();
        }
        return result;
    }

}
