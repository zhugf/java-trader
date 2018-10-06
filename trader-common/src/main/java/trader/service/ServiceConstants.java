package trader.service;

public interface ServiceConstants {

    /**
     * Service所在的applicaiton name
     */
    public static final String SYSPROP_APPLICATION_NAME = "trader.applicationName";


    public static enum AccountState{
        /**
         * 配置已加载, 交易通道未连接
         */
        Created
        /**
         * 交易通道已连接, 初始化中
         */
        ,Initialzing
        /**
         * 交易账户已初始化完毕
         */
        ,Ready
        /**
         * 连接已断开, 账户无法交易
         */
        ,NotReady
    }

    public static enum ConnState{
        /**
         * 已创建未连接
         */
        Initialized,
        /**
         * 连接和登录中
         */
        Connecting,
        /**
         * 已登录
         */
        Connected,
        /**
         * 已断开, 后续会自动重连
         */
        Disconnected,
        /**
         * 连接失败, 不会自动重连
         */
        ConnectFailed
        };

}
