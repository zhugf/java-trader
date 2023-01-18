package trader.simulator;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import trader.common.beans.BeansContainer;
import trader.common.beans.Lifecycle;

public class SimScheduledExecutorService implements ScheduledExecutorService, Lifecycle, SimMarketTimeAware {
    private static final Logger logger = LoggerFactory.getLogger(SimScheduledExecutorService.class);

    private static ZoneId zoneId = ZoneId.systemDefault();

    public class TimeScheduleEntry {
        private Runnable command;
        private long initialDelay;
        private long periodMillis;
        private long nextTriggerMillis;

        public TimeScheduleEntry(Runnable command, long initialDelay, long period, TimeUnit unit){
            this.command = command;
            this.initialDelay = initialDelay;
            this.periodMillis = unit.toMillis(period);
        }

        public void onTimeChanged(LocalDateTime time) {
            long currMillis = time.atZone(zoneId).toInstant().toEpochMilli();

            if ( nextTriggerMillis==0){ //第一次需要round到秒
                nextTriggerMillis = (currMillis/1000)*1000+initialDelay;
                return;
            }
            if ( currMillis>=nextTriggerMillis ){
                while(nextTriggerMillis<=currMillis) {
                    nextTriggerMillis += periodMillis;
                }
                try{
                    command.run();
                }catch(Throwable t) {
                    logger.error("Execute scheduled runnable "+command+" failed "+t.toString(), t);
                }
            }
        }
    }

    private List<TimeScheduleEntry> schedulerEntries = new ArrayList<>();

    private ExecutorService executorService;

    @Override
    public void init(BeansContainer beansContainer) throws Exception {
        SimMarketTimeService mtService = beansContainer.getBean(SimMarketTimeService.class);
        if ( mtService!=null ) {
            mtService.addListener(this);
        }
        executorService = new ThreadPoolExecutor(1, 5, 1, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
    }

    @Override
    public void destroy() {

    }

    @Override
    public void shutdown() {

    }

    @Override
    public List<Runnable> shutdownNow() {
        return Collections.emptyList();
    }

    @Override
    public boolean isShutdown() {
        return false;
    }

    @Override
    public boolean isTerminated() {
        return false;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return false;
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        return executorService.submit(task);
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        return executorService.submit(task, result);
    }

    @Override
    public Future<?> submit(Runnable task) {
        return executorService.submit(task);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        return executorService.invokeAll(tasks);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
        throws InterruptedException
    {
        return executorService.invokeAll(tasks, timeout, unit);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        return executorService.invokeAny(tasks);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException
    {
        return executorService.invokeAny(tasks, timeout, unit);
    }

    @Override
    public void execute(Runnable command) {
        command.run();
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        throw new UnsupportedOperationException();

    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        TimeScheduleEntry scheduler = new TimeScheduleEntry(command, initialDelay, period, unit);
        schedulerEntries.add(scheduler);
        return null;
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        return scheduleAtFixedRate(command, initialDelay, delay, unit);
    }

    @Override
    public void onTimeChanged(LocalDate tradingDay, LocalDateTime actionTime, long timestamp) {
        for(int i=0;i<schedulerEntries.size();i++) {
            schedulerEntries.get(i).onTimeChanged(actionTime);
        }
    }

}
