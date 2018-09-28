package trader.common.beans;

public enum ServiceState {
    /**
     * 未初始化
     */
    Unknown,
    /**
     * 启动中, 不可工作
     */
    Starting,
    /**
     * 启动完毕, 正常工作
     */
    Ready,
    /**
     * 已关闭
     */
    Stopped
}
