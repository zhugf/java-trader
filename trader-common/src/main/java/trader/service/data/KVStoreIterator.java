package trader.service.data;

import java.util.Iterator;

public interface KVStoreIterator extends Iterator<String> {

    public byte[] getValue();

}
