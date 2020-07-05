package trader.service.trade;

import java.util.List;

/**
 * 当日报单.
 * <BR>每个报单有三组唯一序列号:
 * <LI>FrontID+SessionID+OrderRef: 客户端自行维护, 可以随时撤单
 * <LI>ExchangeID+TraderID+OrderLocalID: CTP维护
 * <LI>ExchangeID+OrderSysID: 交易所维护, 可以撤单
 */
public interface Order extends TimedEntity, TradeConstants {

    public static final String ODRATR_CTP_SYS_ID = "ctpSysId";
    public static final String ODRATR_CTP_STATUS = "ctpStatus";
    public static final String ODRATR_CTP_SESSION_ID = "ctpSessionId";
    public static final String ODRATR_CTP_FRONT_ID = "ctpFrontId";

    /**
     * 用于关联Order与Playbook的属性
     */
    public static final String ODRATTR_PLAYBOOK_ID = "pbId";

    /**
     * 关联的TradletGroupId
     */
    public static final String ODRATTR_TRADLET_GROUP_ID = "pbTradletGroupId";

    /**
     * 用于关联Order与Playbook的某个具体动作ID
     */
    public static final String ODRATTR_PLAYBOOK_ACTION_ID = "pbActionId";

    public OrderListener getListener();

    /**
     * 最新的订单状态元组
     */
    public OrderStateTuple getStateTuple();

    /**
     * 订单历史状态元组列表
     */
    public List<OrderStateTuple> getStateTuples();

    /**
     * 本地REF
     */
    public String getRef();

    /**
     * 买卖方向
     */
    public OrderDirection getDirection();

    /**
     * 价格类型
     */
    public OrderPriceType getPriceType();

    /**
     * 开平仓位标志
     */
    public OrderOffsetFlag getOffsetFlags();

    public OrderVolumeCondition getVolumeCondition() ;

    /**
     * 限价
     */
    public long getLimitPrice();

    /**
     * 返回报单资金项
     *
     */
    public long getMoney(OdrMoney mny);

    /**
     * 返回报单资金项
     */
    public long[] getMoney();

    /**
     * 返回报单仓位项
     */
    public int getVolume(OdrVolume vol);

    public List<Transaction> getTransactions();

    public List<String> getTransactionIds();

    public String getAttr(String attr);

    public void setAttr(String attr, String value);

}
