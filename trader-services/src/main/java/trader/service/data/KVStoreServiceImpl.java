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

    @Autowired
    private BeansContainer beansContainer;

    private AbsKVStoreProvider kvStore;

    /**
     * Key: StoreType-StoreId
     * Value: Store
     */
    Map<String, KVStore> storeViews = new HashMap<>();

    @PostConstruct
    public void init() throws Exception {
        File storeGlobalDir = new File( TraderHomeUtil.getDirectory(TraderHomeUtil.DIR_DATA), "store/global");
        kvStore = createStoreProvider(storeGlobalDir.getAbsolutePath());
    }

    @PreDestroy
    private void destroy() {
        if ( kvStore!=null ) {
            kvStore.destroy();
        }
    }

    @Override
    public KVStore getStore(String prefix) {
        if ( StringUtil.isEmpty(prefix)) {
            return kvStore;
        }
        if ( !prefix.endsWith(".")) {
            prefix = prefix+".";
        }
        KVStore storeView = storeViews.get(prefix);
        if ( storeView==null ) {
            storeView = new KVStoreWrapper(prefix, kvStore);
            storeViews.put(prefix, storeView);
        }
        return storeView;
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
