package trader.service;

public interface ServiceConstants {

    /**
     * Service所在的applicaiton name
     */
    public static final String SYSPROP_APPLICATION_NAME = "trader.applicationName";


    public static enum ConnStatus{
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
