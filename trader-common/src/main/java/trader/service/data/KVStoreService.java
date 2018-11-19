package trader.service.data;

public interface KVStoreService {

    /**
     * 返回KVStore
     *
     * @param prefix 自动加prefix前缀, 缺省null
     */
    public KVStore getStore(String prefix);

}
