package trader.service.data;

import org.rocksdb.RocksIterator;

import trader.common.util.StringUtil;

public class RocksDBStoreIterator implements KVStoreIterator{

    private RocksIterator rocksIterator;

    public RocksDBStoreIterator(RocksIterator rocksIterator) {
        this.rocksIterator = rocksIterator;
    }

    @Override
    public boolean hasNext() {
        return rocksIterator.isValid();
    }

    @Override
    public String next() {
        rocksIterator.next();
        if ( rocksIterator.isValid() ) {
            return new String(rocksIterator.key(), StringUtil.UTF8);
        }
        return null;
    }

    @Override
    public byte[] getValue() {
        return rocksIterator.value();
    }

}