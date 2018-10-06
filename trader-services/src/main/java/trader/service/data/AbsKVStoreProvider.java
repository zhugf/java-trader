package trader.service.data;

import trader.common.beans.Lifecycle;

public abstract class AbsKVStoreProvider implements Lifecycle {

    public abstract byte[] get(byte[] key);

    public abstract void put(byte[] key, byte[] data);

}
