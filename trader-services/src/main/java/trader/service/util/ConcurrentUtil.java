package trader.service.util;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.BusySpinWaitStrategy;
import com.lmax.disruptor.LiteBlockingWaitStrategy;
import com.lmax.disruptor.SleepingWaitStrategy;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.YieldingWaitStrategy;

public class ConcurrentUtil {

    public static WaitStrategy createDisruptorWaitStrategy(String waitStrategyCfg) {
        WaitStrategy waitStrategy = null;
        if ( "BusySpin".equalsIgnoreCase(waitStrategyCfg) ) {
            waitStrategy = new BusySpinWaitStrategy();
        }else if ("Sleeping".equalsIgnoreCase(waitStrategyCfg) ){
            waitStrategy = new SleepingWaitStrategy();
        }else if ("LiteBlocking".equalsIgnoreCase(waitStrategyCfg) ){
            waitStrategy = new LiteBlockingWaitStrategy();
        }else if ("Yielding".equalsIgnoreCase(waitStrategyCfg) ){
            waitStrategy = new YieldingWaitStrategy();
        }else {
            waitStrategy = new BlockingWaitStrategy();
        }
        return waitStrategy;
        //Disruptor<AsyncEvent> result = new Disruptor<AsyncEvent>(new AsyncEventFactory(), ringBufferSize, DaemonThreadFactory.INSTANCE, ProducerType.MULTI, waitStrategy);
    }

}
