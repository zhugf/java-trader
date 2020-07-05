package trader.service.repository;

/**
 * 实体对象类型: Default/Playbook/Order/Transaction
 *
 * <LI>Playbook/Order/Transaction类型数据ID的前缀固定
 * <LI>Default类型ID的格式为: tradingDay:type[:More]
 */
public interface BOEntity extends BORepositoryConstants {

    public BOEntityType getType();

    public String getIdPrefix();

    /**
     * 产生唯一UUID
     */
    public String genId();
}
