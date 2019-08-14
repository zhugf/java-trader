package trader.simulator;

import java.util.Map;
import java.util.TreeMap;

import trader.common.util.StringUtil;
import trader.service.data.KVStore;
import trader.service.data.KVStoreIterator;
import trader.service.data.KVStoreService;

public class SimKVStoreService implements KVStoreService {

    private Map<String, String> data = new TreeMap<>();

    private KVStore defaultStore = new MemoryKVStore("");

    class MemoryKVStore implements KVStore{
        private String prefix;

        MemoryKVStore(String prefix){
            this.prefix = prefix;
        }

        @Override
        public byte[] get(String key) {
            String value = getAsString(key);
            if ( StringUtil.isEmpty(value)) {
                return null;
            }
            return value.getBytes(StringUtil.UTF8);
        }

        @Override
        public String getAsString(String key) {
            return data.get(prefix+key);
        }

        @Override
        public void put(String key, byte[] data) {
            put(prefix+key, new String(data, StringUtil.UTF8));
        }

        @Override
        public void put(String key, String value) {
            data.put(prefix+key, value);
        }

        public void aput(String key, byte[] data) {
            put(key, data);
        }

        public void aput(String key, String value) {
            put(key, value);
        }

        @Override
        public KVStoreIterator iterator() {
            return null;
        }

        @Override
        public void delete(String key) {
            data.remove(key);
       }

    }

    @Override
    public KVStore getStore(String prefix) {
        if ( StringUtil.isEmpty(prefix)) {
            return defaultStore;
        }
        return new MemoryKVStore(prefix);
    }

}
