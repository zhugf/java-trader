package trader.service.data;

import trader.common.beans.Lifecycle;
import trader.common.util.StringUtil;

public abstract class AbsKVStoreProvider implements KVStore, Lifecycle {

    public abstract byte[] get(byte[] key);

    public abstract void put(byte[] key, byte[] data);

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

}
