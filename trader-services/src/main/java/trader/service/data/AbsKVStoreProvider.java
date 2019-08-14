package trader.service.data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import trader.common.beans.BeansContainer;
import trader.common.beans.Lifecycle;
import trader.common.util.StringUtil;
import trader.service.concurrent.OrderedExecutor;

public abstract class AbsKVStoreProvider implements KVStore, Lifecycle {
    private final static Logger logger = LoggerFactory.getLogger(AbsKVStoreProvider.class);

    protected OrderedExecutor orderedExecutor;

    protected abstract String getId();

    public abstract byte[] get(byte[] key);

    public abstract void put(byte[] key, byte[] data);

    public abstract void delete(byte[] key);

    @Override
    public void init(BeansContainer beansContainer) throws Exception
    {
        orderedExecutor = beansContainer.getBean(OrderedExecutor.class);
    }

    @Override
    public byte[] get(String key) {
        return get(key.getBytes(StringUtil.UTF8));
    }

    @Override
    public String getAsString(String key) {
        byte[] data = get(key);
        if (data == null) {
            return null;
        }
        return new String(data, StringUtil.UTF8);
    }

    @Override
    public void put(String key, byte[] data) {
        put(key.getBytes(StringUtil.UTF8), data);
    }

    @Override
    public void put(String key, String value) {
        put(key.getBytes(StringUtil.UTF8), value.getBytes(StringUtil.UTF8));
    }

    public void aput(String key, byte[] data) {
        orderedExecutor.execute(getId(), ()->{
            try{
                put(key, data);
            }catch(Throwable t) {
                logger.error(this.toString()+" Put key "+key+" data "+data+" failed", t);
            }
        });
    }

    public void aput(String key, String value) {
        orderedExecutor.execute(getId(), ()->{
            try{
                put(key, value);
            }catch(Throwable t) {
                logger.error(this.toString()+" Put key "+key+" value "+value+" failed", t);
            }
        });
    }


    public void delete(String key) {
        delete(key.getBytes(StringUtil.UTF8));
    }
}
