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

    /**
     * 消息的分类
     */
    public static enum MsgType{
        /**
         * Client->Broker, Client初始化
         */
        InitReq(false), InitResp(true),
        /**
         * Broker->Client, 定期PING
         */
        Ping(false),
        /**
         * Client->Broker, 要求主动关闭
         */
        CloseReq(false), CloseResp(true),
        /**
         * Broker->Client, 要求更新NodeProps
         */
        NodeInfoReq(false), NodeInfoResp(true),
        /**
         * Client->Broker, 要求调用Controller
         */
        ControllerInvokeReq(false), ControllerInvokeResp(true),
        /**
         * Client->Broker, 订阅某个Topic. 消息字段:
         * <LI>topics: string array, topic名称列表
         */
        TopicSubReq(false), TopicSubResp(true),
        /**
         * Client->Broker, 发布消息
         * <LI>topic: string, topic名
         * <LI>topicPayload: ANY, 消息内容
         */
        TopicPubReq(false), TopicPubResp(true),
        /**
         * Broker->Client, 订阅消息推送. 消息字段:
         * <LI>topic: string, topic名
         * <LI>topicPublisher: string, topic发布节点名
         */
        TopicPush(false),
        /**
         * Client->Broker, 查询品种数据, 消息字段:
         * <LI>exchangeable: string, 合约名
         * <LI>dataInfo: string, dataInfo名称
         * <LI>tradingDay: string, 交易日
         */
        DataQueryReq(false),
        /**
         * Broker->Client, 查询结果:消息字段:
         * <LI>data: string, CSV格式查询结果
         */
        DataQueryResp(true);

        private boolean response;
        MsgType(boolean response){
            this.response = response;
        }

        public boolean isResponse() {
            return response;
        }
    };

    /**
     * 服务端主动Ping客户端的间隔, 单位毫秒
     */
    public static final int PING_INTERVAL = 10*1000;

    /**
     * 最大消息长度: 1G
     */
    public static final int MAX_MESSAGE_SIZE = 1*1024*1024*1024;
}
