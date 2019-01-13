package trader.service.tradlet;

/**
 * 交易剧本, 包含了一次量化开平仓的所有细节:
 * <BR>开仓价格,理由, 止损, 止盈, 最长持有时间等等
 */
public interface Playbook {

    /**
     * 全局唯一ID
     */
    public String getId();

    /**
     * 模板参数
     */
    public String getTemplate();

}
