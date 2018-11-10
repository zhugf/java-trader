package trader.service.data;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import trader.common.beans.BeansContainer;
import trader.common.config.ConfigUtil;
import trader.common.util.StringUtil;
import trader.common.util.TraderHomeUtil;

@Service
public class KVStoreServiceImpl implements KVStoreService {
    private final static Logger logger = LoggerFactory.getLogger(KVStoreServiceImpl.class);

    /**
     * 行情数据源定义
     */
    private static final String ITEM_PROVIDER = "KVStore/provider";

    private static final String STORE_DEFAULT = "global";

    @Autowired
    private BeansContainer beansContainer;

    Map<String, AbsKVStoreProvider> stores = new HashMap<>();

    @PostConstruct
    public void init() throws Exception {

    }

    @PreDestroy
    private void destroy() {
        for(AbsKVStoreProvider store:stores.values()) {
            store.destroy();
        }
    }

    @Override
    public synchronized KVStore getStore(String path) throws Exception {
        if (StringUtil.isEmpty(path)) {
            File trader = TraderHomeUtil.getDirectory(TraderHomeUtil.DIR_TRADER);
            path = (new File(trader, STORE_DEFAULT)).getAbsolutePath();
        }
        AbsKVStoreProvider store = stores.get(path);
        if ( store==null ) {
            store = createStoreProvider(path);
            stores.put(path, store);
        }
        return store;
    }

    private AbsKVStoreProvider createStoreProvider(String path) throws Exception {
        String provider = ConfigUtil.getString(ITEM_PROVIDER);
        if (StringUtil.isEmpty(provider)) {
            provider = "rocksdb";
        }
        AbsKVStoreProvider result = null;
        switch(provider.toLowerCase()){
        case "rocksdb":
            result = new RocksDBStore(path);
            break;
        default:
            throw new Exception("Unsupported store provider: "+provider);
        }
        result.init(beansContainer);
        return result;
    }

}
