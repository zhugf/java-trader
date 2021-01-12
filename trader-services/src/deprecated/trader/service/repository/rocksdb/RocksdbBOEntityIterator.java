package trader.service.repository.rocksdb;

import org.rocksdb.RocksIterator;

import trader.common.util.StringUtil;
import trader.service.repository.AbsBOEntity;
import trader.service.repository.AbsBOEntityIterator;
import trader.service.repository.BOEntityIterator;
import trader.service.repository.BORepository;

public class RocksdbBOEntityIterator extends AbsBOEntityIterator implements BOEntityIterator {

    private RocksIterator rocksIterator;

    public RocksdbBOEntityIterator(BORepository repository, AbsBOEntity boEntity, RocksIterator rocksIterator) {
        super(repository, boEntity);
        this.rocksIterator = rocksIterator;
    }

    public boolean hasNext() {
        return rocksIterator.isValid();
    }

    public String next() {
        rocksIterator.next();
        if ( rocksIterator.isValid() ) {
            lastId = new String(rocksIterator.key(), StringUtil.UTF8);
        }
        return lastId;
    }

    public String getData() {
        return new String(rocksIterator.value(), StringUtil.UTF8);
    }

    @Override
    public void close() {
        rocksIterator.close();
    }
}
