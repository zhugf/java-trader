package trader.service.data;

public interface KVStoreService {

    /**
     * 创建KVStore
     * @param path KVStore所在绝对路径, null代表缺省全局KVStore
     */
    public KVStore getStore(String path) throws Exception;

}
