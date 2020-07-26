package trader.service.repository.rocksdb;

import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.DBOptions;
import org.rocksdb.RocksDB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.google.gson.JsonElement;

import trader.common.beans.BeansContainer;
import trader.common.beans.ServiceState;
import trader.common.exception.AppThrowable;
import trader.common.util.ConversionUtil;
import trader.common.util.JsonUtil;
import trader.common.util.StringUtil;
import trader.common.util.TraderHomeUtil;
import trader.common.util.concurrent.DelegateExecutor;
import trader.service.ServiceErrorConstants;
import trader.service.repository.AbsBOEntity;
import trader.service.repository.AbsBORepository;
import trader.service.repository.BOEntity;
import trader.service.repository.BOEntityEmptyIterator;
import trader.service.repository.BOEntityIterator;

/**
 * Rocksdb Repository
 */
//@Service
public class RocksdbBORepository extends AbsBORepository implements ServiceErrorConstants {
    private static final Logger logger = LoggerFactory.getLogger(RocksdbBORepository.class);

    private static final Charset UTF8 = StringUtil.UTF8;

    @Autowired
    private BeansContainer beansContainer;

    private RocksDB rocksdb;

    private RocksdbBOEntity[] entities = null;

    @PostConstruct
    public void init()
    {
        super.init(beansContainer);
        state = ServiceState.Starting;
        File dbDir = new File(TraderHomeUtil.getDirectory(TraderHomeUtil.DIR_WORK), "rocksdb");
        entities= new RocksdbBOEntity[BOEntityType.values().length];
        try {
            dbDir.mkdirs();

            DBOptions dbOptions = new DBOptions();
            dbOptions.setCreateIfMissing(true);
            dbOptions.setCreateMissingColumnFamilies(true);

            List<ColumnFamilyDescriptor> cfDescriptors = new ArrayList<>();
            cfDescriptors.add(new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY));
            cfDescriptors.add(new ColumnFamilyDescriptor(BOEntityType.Order.name().getBytes()));
            cfDescriptors.add(new ColumnFamilyDescriptor(BOEntityType.Playbook.name().getBytes()));
            cfDescriptors.add(new ColumnFamilyDescriptor(BOEntityType.Transaction.name().getBytes()));

            List<ColumnFamilyHandle> cfHandles = new ArrayList<>();
            rocksdb = RocksDB.open(dbOptions, dbDir.getAbsolutePath(), cfDescriptors, cfHandles);
            logger.info("Repository rocksdb is open on "+dbDir);
            //初始化 repository column family
            for(ColumnFamilyHandle cfHandle:cfHandles) {
                String cfName = new String(cfHandle.getName());
                BOEntityType entityType = ConversionUtil.toEnum(BOEntityType.class, cfName);
                if ( entityType!=null ) {
                    entities[entityType.ordinal()] = new RocksdbBOEntity(entityType, cfHandle);
                }
            }
            asyncExecutor = new DelegateExecutor(executorService, 1);
            state = ServiceState.Ready;
        }catch(Throwable t) {
            logger.error("Repository rocksdb open on "+dbDir+" failed", t);
            state = ServiceState.Stopped;
        }
    }

    @PreDestroy
    public void destroy() {
        state = ServiceState.Stopped;
        if ( asyncExecutor!=null ) {
            asyncExecutor.close();
        }
        if ( rocksdb!=null ) {
            rocksdb.close();
        }
    }

    public BOEntity getBOEntity(BOEntityType entityType) {
        return entities[entityType.ordinal()];
    }

    public String load(BOEntityType entityType, String id)
    {
        if ( ServiceState.Ready!=state) {
            return null;
        }
        String result = null;
        try{
            byte[] data = rocksdb.get(entity2handle(entityType), id.getBytes(UTF8));
            if( null!=data ) {
                result = new String(data, UTF8);
            }
        }catch(Throwable t) {
            logger.error(AppThrowable.error2msg(ERR_REPOSITORY_LOAD_FAILED, "加载Rocksdb数据失败: "+id), t);
        }
        return result;
    }

    public BOEntityIterator search(BOEntityType entityType, String queryExpr)
    {
        if ( ServiceState.Ready!=state) {
            return new BOEntityEmptyIterator();
        }
        return new RocksdbBOEntityIterator(this, (AbsBOEntity)getBOEntity(entityType), rocksdb.newIterator(entity2handle(entityType)) );
    }

    public void asynSave(BOEntityType entityType, String id, Object value) {
        if ( ServiceState.Ready!=state) {
            return;
        }
        asyncExecutor.execute(()->{
            try{
                save(entityType, id, JsonUtil.object2json(value));
            }catch(Throwable t) {
                logger.error("asyncSave failed", t);
            }
        });
    }

    public void save(BOEntityType entityType, String id, JsonElement value)
    {
        if ( ServiceState.Ready!=state) {
            return;
        }
        ColumnFamilyHandle handle = entity2handle(entityType);
        try{
            if ( value==null ) {
                rocksdb.delete(handle, id.getBytes(UTF8));
            } else {
                rocksdb.put(handle, id.getBytes(UTF8), value.toString().getBytes(UTF8));
            }
        }catch(Throwable t) {
            logger.error( AppThrowable.error2msg(ERR_REPOSITORY_SAVE_FAILED, "保存Rocksdb数据失败: "+id+" : "+value), t);
        }
    }


    @Override
    public void beginTransaction(boolean readOnly) {
    }

    @Override
    public boolean endTransaction(boolean commit) {
        return false;
    }

    private ColumnFamilyHandle entity2handle(BOEntityType entityType) {
        ColumnFamilyHandle result = null;
        if ( null==entityType) {
            entityType = BOEntityType.Default;
        }
        result = entities[entityType.ordinal()].getHandle();
        return result;
    }
}
