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
        kvStore = account.getStore();
        if ( beansContainer!=null ) {
            ScheduledExecutorService scheduledExecutorService = beansContainer.getBean(ScheduledExecutorService.class);
            loadRefId();
            scheduledExecutorService.scheduleAtFixedRate(()->{
                saveRefId();
            }, 5, 5, TimeUnit.SECONDS);
        }
    }

    public String nextRefId() {
        int ref0 = 0;
        synchronized(this) {
            refId++;
            ref0 = refId;
        }
        //多线程方式设置 000xxx 格式的OrderRef
        StringBuilder builder = new StringBuilder();
        String hex = Integer.toHexString(ref0);
        int prefix0 = 6-hex.length();
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
        return builder.append(hex).toString();
    }

    /**
     * 判断是否要调整OrderRefId
     */
    public void compareAndSetRef(String ref) {
        if ( StringUtil.isEmpty(ref) ) {
            return;
        }
        int firstNonZero = 0;
        for(int i=0;i<ref.length();i++) {
            if ( ref.charAt(i)!='0' ) {
                firstNonZero = i;
                break;
            }
        }
        String refHex = ref.substring(firstNonZero);
        int refValue = Integer.parseInt(refHex, 16);
        if ( refValue<refId ) {
            return;
        }
        synchronized(this) {
            if ( refValue>refId ) {
                refId = refValue;
            }
        }
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
