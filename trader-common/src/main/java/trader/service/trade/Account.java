package trader.service.trade;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import trader.common.util.JsonEnabled;

/**
 * 交易账户
 */
public interface Account extends JsonEnabled {

    public String getId();

    public TxnSession getTxnSession();

    /**
     * 基础属性
     */
    public Properties getConnectionProps();

    /**
     * 根据账户视图过滤当日的报单, null代表不过滤
     */
    public List<Order> getOrders(AccountView view);

    /**
     * 定义的视图, 视图是与交易策略直接关联的
     */
    public Map<String, AccountView> getViews();
}
