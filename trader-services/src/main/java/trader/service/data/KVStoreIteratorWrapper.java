package trader.service.data;

import trader.common.util.StringUtil;

public class KVStoreIteratorWrapper implements KVStoreIterator {

    private String prefix;
    private KVStoreIterator iterator;
    public KVStoreIteratorWrapper(String prefix, KVStoreIterator iterator) {
        this.prefix = prefix;
        this.iterator = iterator;
    }


    @Override
    public boolean hasNext() {
        return iterator.hasNext();
    }

    @Override
    public String next() {
        while(true) {
            String key = next();
            if ( !StringUtil.isEmpty(key) && !key.startsWith(prefix)) {
                continue;
            }
            return key;
        }
    }

    @Override
    public byte[] getValue() {
        return iterator.getValue();
    }

}
