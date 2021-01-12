package trader.service.repository.rocksdb;

import org.rocksdb.ColumnFamilyHandle;

import trader.service.repository.AbsBOEntity;

public class RocksdbBOEntity extends AbsBOEntity{

    private ColumnFamilyHandle handle;

    public RocksdbBOEntity(BOEntityType type, ColumnFamilyHandle handle) {
        super(type);
        this.handle = handle;
    }

    public ColumnFamilyHandle getHandle() {
        return handle;
    }

}
