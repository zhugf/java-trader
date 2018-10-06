package trader.service.trade;

import java.util.Formatter;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import trader.common.beans.BeansContainer;
import trader.common.util.ConversionUtil;
import trader.common.util.StringUtil;
import trader.service.data.KVStore;

/**
 * OrderRef ID顺序生成, 基于KVStore实现序列化和反序列化
 */
public class OrderRefGen {
    private Formatter formatter = new Formatter();
    private volatile int refId;

    private KVStore kvStore;
    private String key;
    private volatile int savedRefId;

    public OrderRefGen(Account account, BeansContainer beansContainer) {
        key = account.getId()+".orderRef";
        if ( beansContainer!=null ) {
            kvStore = beansContainer.getBean(KVStore.class);
            ScheduledExecutorService scheduledExecutorService = beansContainer.getBean(ScheduledExecutorService.class);
            loadRefId();
            scheduledExecutorService.scheduleAtFixedRate(()->{
                saveRefId();
            }, 5, 5, TimeUnit.SECONDS);
        }
    }

    public synchronized String nextRefId() {
        refId++;
        return formatter.format("O%06X", refId).toString();
    }

    private void loadRefId() {
        String value = kvStore.getAsString(key);
        if ( !StringUtil.isEmpty(value)) {
            refId = ConversionUtil.toInt(value);
            savedRefId = refId;
        }
    }

    private void saveRefId() {
        if ( savedRefId!=refId ) {
            savedRefId = refId;
            String value = ""+savedRefId;
            kvStore.put(key, value);
        }
    }
}
