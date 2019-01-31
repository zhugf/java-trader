package trader.service.trade;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import trader.common.beans.BeansContainer;
import trader.common.util.ConversionUtil;
import trader.common.util.StringUtil;
import trader.service.data.KVStore;
import trader.service.data.KVStoreService;

/**
 * <LI>OrderRef ID顺序生成
 * <LI>多账户下每交易日唯一
 * <LI>基于KVStore实现序列化和反序列化
 */
public class OrderRefGenImpl implements OrderRefGen {
    private AtomicInteger refId = new AtomicInteger();

    private KVStore kvStore;
    private String key;
    private volatile int savedRefId;

    public OrderRefGenImpl(BeansContainer beansContainer) {
        KVStoreService kvStoreService = beansContainer.getBean(KVStoreService.class);
        key = "orderRef";
        kvStore = kvStoreService.getStore(null);
        String savedRefId = kvStore.getAsString(key);
        if ( !StringUtil.isEmpty(savedRefId) ) {
            refId.set(ConversionUtil.toInt(savedRefId, true));
        }
        if ( beansContainer!=null ) {
            ScheduledExecutorService scheduledExecutorService = beansContainer.getBean(ScheduledExecutorService.class);
            loadRefId();
            if ( scheduledExecutorService!=null ) {
                scheduledExecutorService.scheduleAtFixedRate(()->{
                    saveRefId();
                }, 5, 5, TimeUnit.SECONDS);
            }
        }
    }

    @Override
    public String nextRefId(String accountId) {
        int ref0 = refId.incrementAndGet();
        //多线程方式设置 000xxx 格式的OrderRef
        StringBuilder builder = new StringBuilder();
        String ref0Str = Integer.toString(ref0);
        int prefix0 = 6-ref0Str.length();
        switch(prefix0) {
        case 5:
            builder.append("00000");
            break;
        case 4:
            builder.append("0000");
            break;
        case 3:
            builder.append("000");
            break;
        case 2:
            builder.append("00");
            break;
        case 1:
            builder.append("0");
            break;
        }
        return builder.append(ref0Str).toString();
    }

    private void loadRefId() {
        String value = kvStore.getAsString(key);
        if ( !StringUtil.isEmpty(value)) {
            savedRefId = ConversionUtil.toInt(value);
            refId.set(savedRefId);
        }
    }

    private void saveRefId() {
        if ( savedRefId!=refId.get() ) {
            savedRefId = refId.get();
            String value = ""+savedRefId;
            kvStore.put(key, value);
        }
    }
}
