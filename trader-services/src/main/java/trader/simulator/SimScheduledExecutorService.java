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
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import trader.common.beans.BeansContainer;
import trader.common.beans.Lifecycle;

public class SimScheduledExecutorService implements ScheduledExecutorService, Lifecycle, SimMarketTimeAware {

    private static ZoneId zoneId = ZoneId.systemDefault();

    public class SimTimeScheduler {
        private Runnable command;
        private long period;
        private TimeUnit unit;

        private long lastTriggerSeconds;
        private long lastTriggerMillis;

        public SimTimeScheduler(Runnable command, long period, TimeUnit unit){
            this.command = command;
            this.period = period;
            this.unit = unit;
        }

        public void onTimeChanged(LocalDateTime time) {
            long epoch = time.atZone(zoneId).toEpochSecond();
            long millis = time.getNano()/1000000;

            if ( lastTriggerSeconds==0){
                lastTriggerSeconds = epoch;
                lastTriggerMillis = time.getNano()/1000000;
                return;
            }
            long nextTriggerSeconds = lastTriggerSeconds;
            long nextTriggerMillis = lastTriggerMillis;

            long periodMillis = unit.toMillis(period);
            nextTriggerMillis += periodMillis;
            if ( nextTriggerMillis> 1000){
                nextTriggerSeconds += (nextTriggerMillis/1000);
                nextTriggerMillis = nextTriggerMillis%1000;
            }

            if ( epoch==nextTriggerSeconds && millis<=nextTriggerMillis){
                command.run();
                lastTriggerSeconds = epoch;
                lastTriggerMillis = millis;
            }
        }
    }


    private List<SimTimeScheduler> schedulerEntries = new ArrayList<>();

    @Override
    public void init(BeansContainer beansContainer) throws Exception {
        SimMarketTimeService mtService = beansContainer.getBean(SimMarketTimeService.class);
        if ( mtService!=null ) {
            mtService.addListener(this);
        }

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
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Future<?> submit(Runnable task) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        throw new UnsupportedOperationException();

    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException {
        throw new UnsupportedOperationException();

    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        throw new UnsupportedOperationException();

    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        throw new UnsupportedOperationException();

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
        SimTimeScheduler scheduler = new SimTimeScheduler(command, period, unit);
        schedulerEntries.add(scheduler);
        return null;
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        SimTimeScheduler scheduler = new SimTimeScheduler(command, delay, unit);
        schedulerEntries.add(scheduler);

        return null;
    }

    @Override
    public void onTimeChanged(LocalDate tradingDay, LocalDateTime actionTime) {
        for(int i=0;i<schedulerEntries.size();i++) {
            schedulerEntries.get(i).onTimeChanged(actionTime);
        }
    }

}
