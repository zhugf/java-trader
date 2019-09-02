package trader.service.util;

import java.util.List;
import java.util.concurrent.TimeUnit;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.BusySpinWaitStrategy;
import com.lmax.disruptor.LiteBlockingWaitStrategy;
import com.lmax.disruptor.LiteTimeoutBlockingWaitStrategy;
import com.lmax.disruptor.SleepingWaitStrategy;
import com.lmax.disruptor.TimeoutBlockingWaitStrategy;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.YieldingWaitStrategy;

import trader.common.util.ConversionUtil;
import trader.common.util.StringUtil;

public class ConcurrentUtil {

    public static WaitStrategy createDisruptorWaitStrategy(String waitStrategyCfg) {
        List<String[]> kvs = StringUtil.splitKVs(waitStrategyCfg);
        String strategyName = kvs.get(0)[0];
        long waitTime = 1000; TimeUnit timeUnit = TimeUnit.MICROSECONDS;
        for(int i=1;i<kvs.size();i++) {
            String[] kv = kvs.get(i);
            if ( kv[0].equalsIgnoreCase("waitTime")) {
                waitTime = ConversionUtil.toLong(kv[1]);
            }else if ( kv[1].equalsIgnoreCase("timeUnit")) {
                timeUnit = ConversionUtil.toEnum(TimeUnit.class, kv[1]);
            }
        }

        WaitStrategy waitStrategy = null;
        switch(strategyName.toLowerCase()) {
        case "busyspin":
            waitStrategy = new BusySpinWaitStrategy();
            break;
        case "sleeping":
            waitStrategy = new SleepingWaitStrategy();
            break;
        case "liteblocking":
            waitStrategy = new LiteBlockingWaitStrategy();
            break;
        case "yielding":
            waitStrategy = new YieldingWaitStrategy();
            break;
        case "BlockingWait":
            waitStrategy = new BlockingWaitStrategy();
            break;
        case "litetimeoutblockingwait":
            waitStrategy = new LiteTimeoutBlockingWaitStrategy(waitTime, timeUnit);
            break;
        case "timeoutblockingwait":
            waitStrategy = new TimeoutBlockingWaitStrategy(waitTime, timeUnit);
            break;
        default:
            throw new RuntimeException(waitStrategyCfg+" is not supported");
        }
        return waitStrategy;
        //Disruptor<AsyncEvent> result = new Disruptor<AsyncEvent>(new AsyncEventFactory(), ringBufferSize, DaemonThreadFactory.INSTANCE, ProducerType.MULTI, waitStrategy);
    }

}
