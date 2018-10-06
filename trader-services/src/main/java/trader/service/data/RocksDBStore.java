package trader.service.data;

import java.io.File;

import org.rocksdb.RocksDB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import trader.common.beans.BeansContainer;
import trader.common.util.TraderHomeUtil;

/**
 * 基于RocksDB实现KVStore
 */
public class RocksDBStore extends AbsKVStoreProvider {
    private final static Logger logger = LoggerFactory.getLogger(RocksDBStore.class);

    private RocksDB db;

    @Override
    public void init(BeansContainer beansContainer) throws Exception {
        File dailyDataDir = (new File(TraderHomeUtil.getTraderDailyDir(),"/datastore")).getAbsoluteFile();
        dailyDataDir.mkdirs();
        db = RocksDB.open(dailyDataDir.getAbsolutePath());
        logger.info("RocksDB kvstore is open on "+dailyDataDir);
    }

    @Override
    public void destory() {
        if ( null!=db ) {
            db.close();
        }
    }

    @Override
    public byte[] get(byte[] key) {
        try{
            return db.get(key);
        }catch(Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void put(byte[] key, byte[] data) {
        try{
            db.put(key, data);
        }catch(Exception e) {
            throw new RuntimeException(e);
        }
    }

}
