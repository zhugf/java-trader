package trader.service.data;

/**
 * KVStore简单包装, 附加某个前缀
 */
public class KVStoreWrapper implements KVStore {
    private String prefix;
    private KVStore delegate;

    public KVStoreWrapper(String prefix, KVStore delegate) {
        this.prefix = prefix;
        this.delegate = delegate;
    }

    @Override
    public byte[] get(String key) {
        return delegate.get(prefix+key);
    }

    @Override
    public String getAsString(String key) {
        return delegate.getAsString(prefix+key);
    }

    @Override
    public void put(String key, byte[] data) {
        delegate.put(prefix+key, data);
    }

    @Override
    public void put(String key, String value) {
        delegate.put(prefix+key, value);
    }

    public void aput(String key, byte[] data) {
        delegate.aput(key, data);
    }

    public void aput(String key, String value) {
        delegate.aput(prefix+key, value);
    }

    public void delete(String key) {
        delegate.delete(prefix+key);
    }

    @Override
    public KVStoreIterator iterator() {
        return new KVStoreIteratorWrapper(prefix, delegate.iterator());
    }

}
