package trader.common.util.concurrent;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DelegateExecutor implements Executor, AutoCloseable
{    private static final Logger logger = LoggerFactory.getLogger(DelegateExecutor.class);

    private ExecutorService executorService;
    private int maxThreads;
    private AtomicInteger currThreads = new AtomicInteger();
    private LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
    private volatile boolean stopped;

    public DelegateExecutor(ExecutorService executorService, int maxThreads) {
        this.executorService = executorService;
        this.maxThreads = maxThreads;
    }

    @Override
    public void close() {
        stopped = true;
    }

    @Override
    public void execute(Runnable cmd) {
        queue.offer(cmd);
        synchronized(this){
            if ( currThreads.get()<maxThreads ) {
                currThreads.incrementAndGet();
                executorService.execute(()->{
                    threadFunc();
                });
            }
        }
    }

    private void threadFunc() {
        try {
            while(!stopped) {
                Runnable cmd = null;
                try{
                    cmd = queue.poll(500, TimeUnit.MILLISECONDS);
                }catch(Throwable t) {}
                if ( null==cmd ) {
                    break;
                }
                try{
                    cmd.run();
                }catch(Throwable t) {
                    logger.error("delegate executor "+cmd+" got unexpected exception: "+t.toString(), t);
                }
            }
        }finally {
            currThreads.decrementAndGet();
        }
    }

}
