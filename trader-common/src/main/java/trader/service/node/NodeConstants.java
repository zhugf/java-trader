package trader.service.node;

public interface NodeConstants {

    public static final String URI_WS_NODE = "/api/ws/node";

    /**
     * 节点的分类
     */
    public static enum NodeType{Trader, GenericClient};

    /**
     * 节点状态
     */
    public static enum NodeState{NotConfigured, Connecting, Initializing, Ready, Closing, Closed};

    public static final String TYPE_SUFFIX_REQ = "Req";
    public static final String TYPE_SUFFIX_REP = "Rep";
    public static final String TYPE_SUFFIX_PUSH = "Push";

    /**
     * Client->Broker, Client初始化
     */
    public static final String TYPE_INIT_REQ = "InitReq";
    public static final String TYPE_INIT_REP = "InitRep";

    /**
     * Broker->Client, 定期PING
     */
    public static final String TYPE_PING_REQ = "PingReq";
    public static final String TYPE_PING_REP = "PingRep";

    /**
     * Client->Broker, 要求主动关闭
     */
    public static final String TYPE_CLOSE_REQ = "CloseReq";
    public static final String TYPE_CLOSE_REP = "CloseRep";

    /**
     * Broker->Client, 要求更新NodeProps
     */
    public static final String TYPE_NODEINFO_REQ = "NodeInfoReq";
    public static final String TYPE_NODEINFO_REP = "NodeInfoRep";

    /**
     * Client->Broker, 订阅某个Topic. 消息字段:
     * <LI>topics: string array, topic名称列表
     */
    public static final String TYPE_TOPICSUB_REQ = "TopicSubReq";
    public static final String TYPE_TOPICSUB_REP = "TopicSubRep";

    /**
     * Client->Broker, 发布消息
     * <LI>topic: string, topic名
     * <LI>topicPayload: ANY, 消息内容
     */
    public static final String TYPE_TOPICPUB_REQ = "TopicPubReq";
    public static final String TYPE_TOPICPUB_REP = "TopicPubRep";

    /**
     * Broker->Client, 订阅消息推送. 消息字段:
     * <LI>topic: string, topic名
     * <LI>topicPublisher: string, topic发布节点名
     */
    public static final String TYPE_TOPIC_PUSH = "TopicPush";

    /**
     * Client->Broker, 查询品种数据, 消息字段:
     * <LI>exchangeable: string, 合约名
     * <LI>dataInfo: string, dataInfo名称
     * <LI>tradingDay: string, 交易日
     */
    public static final String TYPE_DATAQUERY_REQ = "DataQueryReq";
    /**
     * Broker->Client, 查询结果:消息字段:
     * <LI>data: string, CSV格式查询结果
     */
    public static final String TYPE_DATAQUERY_REP = "DataQueryRep";

    /**
     * 服务端主动Ping客户端的间隔, 单位毫秒
     */
    public static final int PING_INTERVAL = 10*1000;

    /**
     * 最大消息长度: 1G
     */
    public static final int MAX_MESSAGE_SIZE = 1*1024*1024*1024;

    /**
     * 账户资金随着行情发生变化, 每3-5秒推送1次.
     */
    public static final String TOPIC_TRADE_ACCOUNT_MONEY = "/trade/account/money";

    /**
     * 账户基础信息, 每分钟推送1次
     */
    public static final String TOPIC_TRADE_ACCOUNT_INFO = "/trade/account/info";

    public static final String TOPIC_TRADE_ORDER = "/trade/order";

    public static final String TOPIC_TRADE_TXN = "/trade/txn";

    public static final String TOPIC_TRADLET_PLAYBOOK = "/tradlet/playbook";
}
