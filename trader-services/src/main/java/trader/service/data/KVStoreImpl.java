package trader.service.data;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import trader.common.beans.BeansContainer;
import trader.common.config.ConfigUtil;
import trader.common.util.StringUtil;

@Service
public class KVStoreImpl implements KVStore {
    private final static Logger logger = LoggerFactory.getLogger(KVStoreImpl.class);

    /**
     * 行情数据源定义
     */
    public static final String ITEM_PROVIDER = "KVStore/provider";

    @Autowired
    private BeansContainer beansContainer;

    private AbsKVStoreProvider store;

    @PostConstruct
    public void init() throws Exception {
        store = createStoreProvider();
    }

    @PreDestroy
    private void destroy() {
        if (null!=store) {
            store.destory();
        }
    }

    @Override
    public byte[] get(String key) {
        return store.get(key.getBytes(StringUtil.UTF8));
    }

    @Override
    public String getAsString(String key) {
        byte[] data = get(key);
        if ( data==null ) {
            return null;
        }
        return new String(data, StringUtil.UTF8);
    }

    @Override
    public void put(String key, byte[] data) {
        store.put(key.getBytes(StringUtil.UTF8), data);
    }

    @Override
    public void put(String key, String value) {
        store.put(key.getBytes(StringUtil.UTF8), value.getBytes(StringUtil.UTF8));
    }

    private AbsKVStoreProvider createStoreProvider() throws Exception {
        String provider = ConfigUtil.getString(ITEM_PROVIDER);
        if (StringUtil.isEmpty(provider)) {
            provider = "rocksdb";
        }
        AbsKVStoreProvider result = null;
        switch(provider.toLowerCase()){
        case "rocksdb":
            result = new RocksDBStore();
            break;
        default:
            throw new Exception("Unsupported store provider: "+provider);
        }
        result.init(beansContainer);
        return result;
    }

}
