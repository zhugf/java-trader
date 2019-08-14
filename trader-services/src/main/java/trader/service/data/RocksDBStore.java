package trader.service.data;

import java.io.File;

import org.rocksdb.RocksDB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import trader.common.beans.BeansContainer;

/**
 * 基于RocksDB实现KVStore
 */
public class RocksDBStore extends AbsKVStoreProvider {
    private final static Logger logger = LoggerFactory.getLogger(RocksDBStore.class);

    private String id;
    private String path;
    private RocksDB db;

    public RocksDBStore(String path) {
        this.id = "rocksDb";
        this.path = path;
    }

    @Override
    public void init(BeansContainer beansContainer) throws Exception
    {
        super.init(beansContainer);
        File rocksdbDir = new File(path).getAbsoluteFile();
        rocksdbDir.mkdirs();
        db = RocksDB.open(rocksdbDir.getAbsolutePath());
        logger.info("RocksDB kvstore is open on "+rocksdbDir);
    }

    @Override
    public void destroy() {
        if ( null!=db ) {
            db.close();
        }
    }

    public String getId() {
        return id;
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

    public void delete(byte[] key) {
        try{
            db.delete(key);
        }catch(Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public KVStoreIterator iterator() {
        return new RocksDBStoreIterator(db.newIterator());
    }

    public String toString() {
        return "RocksDBStore["+path+"]";
    }
}
