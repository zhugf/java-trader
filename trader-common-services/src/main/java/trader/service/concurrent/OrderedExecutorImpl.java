package trader.service.concurrent;

import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.util.concurrent.MoreExecutors;

@Service
public class OrderedExecutorImpl implements OrderedExecutor {

    private static final int THREAD_COUNT = 10;

    @Autowired
    private ExecutorService executorService;

    private Executor[] executors;

    private Random random = new Random();

    @PostConstruct
    public void init() {
        executors = new Executor[THREAD_COUNT];
        for(int i=0;i<this.executors.length;i++) {
            executors[i] = MoreExecutors.newSequentialExecutor(executorService);
        }
    }

    @Override
    public void execute(String key, Runnable cmd) {
        int hash = 0;
        if ( key==null ) {
            hash = random.nextInt();
        }else {
            hash = key.hashCode();
        }
        hash &= 0X7FFF;
        executors[hash % THREAD_COUNT].execute(cmd);
    }

}
