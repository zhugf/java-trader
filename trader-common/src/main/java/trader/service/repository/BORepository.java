package trader.service.repository;

import com.google.gson.JsonElement;

import trader.common.exception.AppException;

/**
 * 实体对象的持久存储/访问, 基于RocksDB
 */
public interface BORepository extends BORepositoryConstants {

    public BOEntity getBOEntity(BOEntityType entityType);

    /**
     * 加载
     * @param entity null代表 default family
     * @param entityId
     * @return
     * @throws AppException
     */
    public String load(BOEntityType entityType, String entityId);

    /**
     * 查询/遍历数据
     *
     * @param entityType
     * @param queryExpr
     * @return
     */
    public BOEntityIterator search(BOEntityType entityType, String queryExpr);

    /**
     * 独立线程异步保存
     */
    public void asynSave(BOEntityType entityType, String id, JsonElement value);

    /**
     * 同步保存数据
     *
     * @param entity null代表default family
     */
    public void save(BOEntityType entityType, String id, JsonElement json);

    public void beginTransaction(boolean readOnly);

    public boolean endTransaction(boolean commit);
}