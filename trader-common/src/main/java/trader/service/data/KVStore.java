package trader.service.data;

/**
 * 一个简单的Key-Value的存储
 */
public interface KVStore {

    public byte[] get(String key);

    public String getAsString(String key);

    public void put(String key, byte[] data);

    public void put(String key, String value);
}
