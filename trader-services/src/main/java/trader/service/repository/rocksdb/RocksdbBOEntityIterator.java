package trader.service.repository.rocksdb;

import org.rocksdb.RocksIterator;

import trader.common.util.StringUtil;
import trader.service.repository.BOEntityIterator;

public class RocksdbBOEntityIterator implements BOEntityIterator {

    private RocksIterator rocksIterator;

    public RocksdbBOEntityIterator(RocksIterator rocksIterator) {
        this.rocksIterator = rocksIterator;
    }

    public boolean hasNext() {
        return rocksIterator.isValid();
    }

    public String next() {
        rocksIterator.next();
        if ( rocksIterator.isValid() ) {
            return new String(rocksIterator.key(), StringUtil.UTF8);
        }
        return null;
    }

    public String getValue() {
        return new String(rocksIterator.value(), StringUtil.UTF8);
    }

    @Override
    public void close() {
        rocksIterator.close();
    }

}
